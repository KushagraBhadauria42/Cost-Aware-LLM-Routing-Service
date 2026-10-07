# Cost-Aware LLM Routing Service

A Spring Boot service that sits in front of multiple LLM providers (and a free, deterministic
mock provider) and routes each request to the cheapest/fastest/highest-quality model for the
caller's stated priority — with Redis caching, per-client API keys + rate limiting, cost/usage
tracking, Prometheus/Grafana observability, and a benchmark harness to quantify the savings.

## Architecture

```mermaid
flowchart TB
    Client[API client] -->|X-API-Key| Filter[ApiKeyAuthFilter]
    Filter -->|401 missing/invalid key| Client
    Filter -->|429 rate limit/quota| Client
    Filter --> Controller[ChatController POST /v1/chat]

    Controller --> ChatService
    ChatService -->|1. cache lookup| Cache[(Redis\nChatCacheService)]
    Cache -->|hit: instant, $0| ChatService
    ChatService -->|2. miss: rank candidates| RoutingEngine
    RoutingEngine --> Cheap[CheapRoutingStrategy]
    RoutingEngine --> Fast[FastRoutingStrategy]
    RoutingEngine --> Quality[QualityRoutingStrategy]
    RoutingEngine --> Auto[AutoRoutingStrategy]
    RoutingEngine -->|ranked ModelSpec list| ChatService

    ChatService -->|3. try candidates in order,\nfall back on failure| Providers
    subgraph Providers [LlmProvider implementations]
        Mock[MockProvider free/fast]
        Groq[GroqProvider]
        OpenRouter[OpenRouterProvider]
        Gemini[GeminiProvider]
    end

    ChatService -->|4. cache the answer| Cache
    ChatService -->|5. record cost + metrics| UsageLogService
    UsageLogService --> DB[(Postgres\nusage_log, api_keys)]
    UsageLogService --> Metrics[Micrometer /actuator/prometheus]
    Metrics --> Prometheus[(Prometheus)]
    Prometheus --> Grafana[(Grafana dashboard)]

    Admin[Admin] -->|X-Admin-Secret| AdminController[POST /admin/api-keys]
    AdminController --> DB

    BenchmarkRunner[BenchmarkRunner\nCommandLineRunner, opt-in] -.->|replays 57 fixed prompts,\nnever touches DB/metrics| RoutingEngine
    BenchmarkRunner -.-> Mock
```

Key point the diagram tries to make explicit: the **benchmark harness is a separate, dotted-line
path** — it reuses `RoutingEngine`/providers to measure realistic behavior, but deliberately never
writes to `usage_log` or Micrometer, so repeated benchmark runs can't pollute real usage stats or
the live dashboard (see [Design decisions](#design-decisions)).

## Project layout

```
src/main/java/com/example/llmrouter/
  chat/        POST /v1/chat controller, service, DTOs, Priority enum
  routing/     ModelRegistry (models.yml), RoutingStrategy impls, RoutingEngine
  provider/    LlmProvider interface + mock/groq/openrouter/gemini adapters (Resilience4j)
  cache/       Redis exact-match response cache
  apikey/      API key issuance + authentication
  ratelimit/   Redis fixed-window rate limiting/quota
  usage/       Cost calculation, usage_log persistence, /v1/usage, CostCalculator
  benchmark/   Fixed-prompt benchmark harness (Stage 9)
  error/       Global exception handling
```

## Setup

### Option A — one command, everything in Docker

```powershell
docker compose up -d --build
```

This builds the app image (multi-stage `Dockerfile`) and starts Postgres, Redis, Prometheus,
Grafana, and the app together, wired on one Docker network. The app is reachable at
`http://localhost:8080`, Grafana at `http://localhost:3000` (admin/admin), Prometheus at
`http://localhost:9090`.

Copy `.env.example` to `.env` first if you want real provider API keys, an admin secret, etc. —
`docker compose` automatically reads `.env` for the `${VAR:-default}` substitutions used in
`docker-compose.yml`.

### Option B — app on the host, infra in Docker (used throughout development)

```powershell
docker compose up -d postgres redis prometheus grafana
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

### Running tests

```powershell
.\mvnw.cmd verify
```

`95/95` tests pass (unit tests + WireMock provider tests + Testcontainers Postgres/Redis
integration tests).

## Example requests

Create an API key (admin-only, requires `ADMIN_SECRET`):

```powershell
curl -X POST http://localhost:8080/admin/api-keys `
  -H "X-Admin-Secret: $env:ADMIN_SECRET" `
  -H "Content-Type: application/json" `
  -d '{"clientName":"demo-client","rateLimitPerMinute":30,"dailyQuota":500}'
```

Response includes the raw key once (`apiKey` field) — only its SHA-256 hash is stored.

Chat request, letting AUTO pick the model:

```powershell
curl -X POST http://localhost:8080/v1/chat `
  -H "X-API-Key: llmr_...` -H "Content-Type: application/json" `
  -d '{"prompt":"Summarize the French Revolution in two sentences.","priority":"AUTO"}'
```

Force the cheapest route:

```powershell
curl -X POST http://localhost:8080/v1/chat `
  -H "X-API-Key: llmr_..." -H "Content-Type: application/json" `
  -d '{"prompt":"hi","priority":"CHEAP"}'
```

Check your own cost/usage summary:

```powershell
curl http://localhost:8080/v1/usage -H "X-API-Key: llmr_..."
```

Raw Prometheus metrics:

```powershell
curl http://localhost:8080/actuator/prometheus
```

## Design decisions

- **Routing is pluggable per-priority, not a single scoring function.** `RoutingEngine` maps
  `Priority -> RoutingStrategy` (`CHEAP`/`FAST`/`QUALITY`/`AUTO`), and each strategy both `select()`s
  a single best candidate and `rank()`s all candidates so `ChatService` can fall back through the
  ordered list on failure, instead of hard-failing on the first provider's error.
- **Redis exact-match cache keys on normalized prompt + priority**, not raw prompt — trims/
  lowercases/collapses whitespace so trivial variations share a cache entry; a cache hit is
  recorded with `latencyMs=0`/`virtualCost=0` but still counts against the caller's rate limit
  (the filter runs before the cache is even checked, and rate limits protect the HTTP surface,
  a separate concern from provider cost).
- **API keys are stored as SHA-256 hashes only**; the raw key is returned once at creation and
  never persisted or logged. The admin endpoint itself is guarded by a separate, constant-time-
  compared `X-Admin-Secret` header (`MessageDigest.isEqual`) to avoid a timing side-channel.
- **Cost tracking computes two numbers per request**: `virtualCost` (what was actually spent —
  `0` on a cache hit or when the free mock/openrouter model answered) and `baselineCost` (what the
  most expensive configured model would have charged for the same tokens) — "savings" is always
  `baseline - virtual`, never its own stored field, so it can't drift from its two sources.
- **Metrics reuse the exact same already-computed cost values as `usage_log`** (same call site in
  `UsageLogService.record()`), instead of instrumenting each provider call separately — guarantees
  the dashboard and the `/v1/usage` API can never numerically disagree.
- **The benchmark harness does NOT write to `usage_log` or Micrometer.** It replays a fixed,
  repeatable 57-prompt set under every strategy purely to produce a report; routing it through the
  same recording path as real traffic would inflate real client usage stats and pollute the
  Grafana dashboard with fake, repeated request volume. It still calls the identical
  `CostCalculator`/`ModelRegistry.mostExpensive()` formula as production, so the cost *numbers*
  can't drift — only persistence is skipped. See `BenchmarkService`'s class javadoc for the full
  rationale.
- **Benchmarking is free and rate-limit-safe by default**: token counts come from the free local
  `MockProvider`, and non-mock candidates' latency is *simulated* by jittering `models.yml`'s
  already-"virtual, made-up" `avg-latency-ms` field (±15%, fixed seed for reproducibility) rather
  than making a real network call. Opting into `useRealProviders=true` calls real providers but
  gracefully falls back to the simulated path per-sample if a call fails (e.g. no API key).

## Benchmark results

**Mock-only run** (`BENCHMARK_ENABLED=true`, `BENCHMARK_USE_REAL_PROVIDERS=false` — the default;
zero real network calls, zero API cost), 57 fixed prompts replayed under all 5 strategies:

| strategy          | prompts | virtualCost$ | baselineCost$ | savings% | avgMs | p50Ms | p95Ms | realCalls |
|--------------------|--------:|-------------:|--------------:|---------:|------:|------:|------:|----------:|
| ALWAYS_EXPENSIVE   |      57 |       0.0746 |        0.0746 |     0.0% | 404.0 | 398.0 | 450.0 |         0 |
| CHEAP              |      57 |       0.0000 |        0.0746 |   100.0% | 176.8 | 172.0 | 296.0 |         0 |
| FAST               |      57 |       0.0000 |        0.0746 |   100.0% | 174.3 | 172.0 | 297.0 |         0 |
| QUALITY            |      57 |       0.0298 |        0.0746 |    60.0% | 601.9 | 607.0 | 679.0 |         0 |
| AUTO               |      57 |       0.0099 |        0.0746 |    86.7% | 297.0 | 251.0 | 660.0 |         0 |

> **Note on these numbers**: this environment has no real provider API keys configured, so a
> genuine "hybrid" run (`BENCHMARK_USE_REAL_PROVIDERS=true` with at least one real key present,
> producing `realCalls > 0` for non-mock strategies) was not captured here. These are the
> mock-only numbers — honest about what they are, not a substitute for a hybrid run. To produce
> one: set a real `GROQ_API_KEY`/`GEMINI_API_KEY`/`OPENROUTER_API_KEY` in `.env` and re-run with
> `BENCHMARK_USE_REAL_PROVIDERS=true`; expect `realCalls` to equal the prompt count for strategies
> that route to that provider (e.g. QUALITY → gemini), and latency/cost to reflect real API
> responses instead of simulated jitter for those rows.

Reading the table: `CHEAP`/`FAST` always route to the free mock model (100% savings, since it's
`$0`), `ALWAYS_EXPENSIVE` *is* the baseline by construction (always exactly 0% savings), `QUALITY`
always picks the one HIGH-quality-tier model (gemini, $0.02/1k — cheaper than the baseline groq at
$0.05/1k, hence a consistent 60% savings), and `AUTO` mixes strategies per prompt length/code-block
content, landing between the two extremes.

## Demo script

1. `docker compose up -d --build` — show all 5 containers come up healthy.
2. Create an API key via `/admin/api-keys`, highlight that the raw key is shown exactly once.
3. Send one `/v1/chat` request with `priority: QUALITY` — point out `routingReason`, `provider`,
   `model` in the response.
4. Repeat the exact same request — show `cacheHit: true`, `latencyMs: 0`.
5. Send requests past the configured rate limit — show the `429` + `Retry-After` header.
6. `GET /v1/usage` — show `totalSavings`/`savingsPercent` for that client.
7. Open Grafana (`localhost:3000`) — show requests/sec, cache hit rate, savings panels updating
   live.
8. Set `BENCHMARK_ENABLED=true` and restart the app (or run via `mvnw spring-boot:run`) — show the
   printed benchmark table and `benchmark-report.txt`, and explain why it doesn't appear in the
   Grafana dashboard or `/v1/usage` (see design decisions above).

## Likely interview questions

**Q: Why not just always call the cheapest model?**
A: Quality still matters for some requests (e.g. long/code-heavy prompts) — `AUTO` routes those to
the HIGH-quality-tier model, trading some cost for better answers, while still saving on
short/simple prompts. Forcing `CHEAP` everywhere is available but is a deliberate tradeoff the
caller opts into via `priority`, not the only mode.

**Q: How do you keep the fallback chain from hammering a dead provider?**
A: Resilience4j `@CircuitBreaker` per provider — after enough failures in the sliding window, the
circuit opens and calls fail fast (no network call at all) until the wait duration elapses, so a
persistently-down provider doesn't add latency to every request that ranks it first.

**Q: Why hash API keys instead of storing them plaintext (or even encrypted)?**
A: The server never needs to recover the original key — it only needs to verify a presented key
matches. Hashing (SHA-256) means a database leak can't be used to impersonate any client, which a
reversible encryption scheme couldn't guarantee if the encryption key also leaked.

**Q: Does a cache hit count against the rate limit? Why?**
A: Yes. `ApiKeyAuthFilter` runs before `ChatService` ever checks the cache, so it can't know in
advance whether a request will hit. More fundamentally, rate limits protect the HTTP API surface
(fairness across clients), which is a different concern from provider cost — cost is already
correctly `$0` for cache hits via `virtualCost`.

**Q: Why two cost numbers (virtual + baseline) instead of just reporting savings directly?**
A: Storing savings as its own field risks it silently drifting from its two sources if either one
is updated and the other forgotten. Deriving `baseline - virtual` on read (API, Prometheus, Grafana
PromQL) guarantees it can never disagree with the two counters it depends on.

**Q: Why doesn't the benchmark write to the same `usage_log`/metrics as real traffic?**
A: Benchmark runs are a fixed, repeatable, potentially-frequent synthetic workload (for
development/CI), not real customer traffic. If it recorded through the same path, it would inflate
every client's `/v1/usage` numbers and corrupt the live Grafana dashboard's request volume/cache-
hit-rate/cost panels with non-customer data. It still reuses the same cost *formula*
(`CostCalculator`/`ModelRegistry.mostExpensive()`), so the numbers it reports are trustworthy and
consistent with production — only the recording step is skipped.

**Q: How would you extend this to a real multi-tenant production system?**
A: Likely candidates: per-tenant model allow-lists/budgets, async/streaming responses, a real token
counter per provider's tokenizer instead of an estimate, horizontal scaling of the rate limiter
(already Redis-backed, so this mostly already works), and replacing the fixed-window rate limiter
with a sliding-window or token-bucket algorithm to avoid burst-at-boundary edge cases.

**Q: What's the biggest single point of failure in this design?**
A: Redis — it backs both the response cache and the rate limiter. The code is written so a cache
miss/Redis outage degrades to "always call a provider" (cache features fail open to normal
routing) rather than hard-failing requests, but rate limiting would need an explicit decision
(fail open and allow all requests, or fail closed and reject) if Redis were ever down in
production; currently it is not explicitly handled and would surface as a 5xx.
