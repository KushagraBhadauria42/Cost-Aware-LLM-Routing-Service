package com.example.llmrouter.benchmark;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs the Stage 9 benchmark once on application startup, if and only if
 * {@code llm.benchmark.enabled=true} (default {@code false}, so a normal {@code spring-boot:run}
 * never triggers it). Prints a plain-text report to stdout and also writes it to
 * {@code benchmark-report.txt} in the working directory.
 */
@Component
@ConditionalOnProperty(prefix = "llm.benchmark", name = "enabled", havingValue = "true")
public class BenchmarkRunner implements CommandLineRunner {

    private final BenchmarkService benchmarkService;
    private final BenchmarkProperties properties;

    public BenchmarkRunner(BenchmarkService benchmarkService, BenchmarkProperties properties) {
        this.benchmarkService = benchmarkService;
        this.properties = properties;
    }

    @Override
    public void run(String... args) throws IOException {
        BenchmarkReport report = benchmarkService.run(List.of(BenchmarkStrategy.values()), properties.isUseRealProviders());
        String formatted = BenchmarkReportFormatter.format(report);
        System.out.println(formatted);
        Files.writeString(Path.of("benchmark-report.txt"), formatted);
    }
}
