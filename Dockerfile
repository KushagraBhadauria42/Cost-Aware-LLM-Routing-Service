# ---- Build stage ----
# Uses the full JDK + Maven Wrapper to produce an executable fat jar. Kept as a separate stage
# so the final runtime image doesn't carry the JDK, build cache, or source tree.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy wrapper + POM first so dependency resolution is cached in its own Docker layer and only
# re-runs when pom.xml actually changes, not on every source edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# mvnw may have CRLF line endings when checked out on Windows, which breaks its "#!/bin/sh"
# shebang inside this Linux container ("./mvnw: not found"). Normalize to LF before chmod.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ---- Runtime stage ----
# JRE-only image (no JDK/Maven) to keep the shipped image small and reduce attack surface.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Runs as a non-root user (OWASP: avoid running application containers as root).
RUN useradd --no-create-home --shell /usr/sbin/nologin appuser
COPY --from=build /app/target/llm-router-service-*.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
