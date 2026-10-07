package com.example.llmrouter.benchmark;

/**
 * Renders a {@link BenchmarkReport} as a fixed-width plain-text table suitable for console output
 * or writing to a file - simplest possible "report" format, consistent with this project's general
 * preference for plain, dependency-free formatting over introducing a reporting/templating library
 * for a single use case.
 */
public final class BenchmarkReportFormatter {

    private BenchmarkReportFormatter() {
    }

    public static String format(BenchmarkReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "Benchmark report: %d prompts, useRealProviders=%s%n",
                report.promptCount(), report.usedRealProviders()));
        sb.append(String.format(
                "%-16s %8s %14s %14s %10s %10s %10s %10s %10s%n",
                "strategy", "prompts", "virtualCost$", "baselineCost$", "savings%", "avgMs", "p50Ms", "p95Ms", "realCalls"));
        for (BenchmarkStrategyReport r : report.strategyReports()) {
            sb.append(String.format(
                    "%-16s %8d %14.4f %14.4f %9.1f%% %10.1f %10.1f %10.1f %10d%n",
                    r.strategy(), r.promptCount(), r.totalVirtualCost(), r.totalBaselineCost(),
                    r.savingsPercent(), r.avgLatencyMs(), r.p50LatencyMs(), r.p95LatencyMs(), r.realNetworkCalls()));
        }
        return sb.toString();
    }
}
