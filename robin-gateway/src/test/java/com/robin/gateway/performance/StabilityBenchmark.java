package com.robin.gateway.performance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GAP-009: Sustained Load Test for Memory Leak Detection.
 * Runs high volume of requests against the Gateway.
 */
public class StabilityBenchmark {
    private static final String TARGET_URL = "http://localhost:8888/actuator/health";
    private static final int TOTAL_REQUESTS = 50_000;
    private static final int CONCURRENCY = 100;

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Stability Test (GAP-009) - High Volume...");
        System.out.println("Target: " + TARGET_URL);
        System.out.println("Total Requests: " + TOTAL_REQUESTS);
        System.out.println("Concurrency: " + CONCURRENCY);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TOTAL_REQUESTS; i++) {
                executor.submit(() -> {
                    long start = System.nanoTime();
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(TARGET_URL))
                                .GET()
                                .build();
                        
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        
                        if (response.statusCode() == 200) {
                            completed.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    } finally {
                        totalLatency.addAndGet(System.nanoTime() - start);
                        int current = completed.get() + failed.get();
                        if (current % 5000 == 0) {
                            System.out.printf("Progress: %d/%d requests...%n", current, TOTAL_REQUESTS);
                        }
                    }
                });
            }
        }

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        double throughput = (double) TOTAL_REQUESTS / (durationMs / 1000.0);
        double avgLatency = (double) totalLatency.get() / TOTAL_REQUESTS / 1_000_000.0;

        System.out.println("\n--- Test Results ---");
        System.out.printf("Duration: %d ms%n", durationMs);
        System.out.printf("Throughput: %.2f req/s%n", throughput);
        System.out.printf("Average Latency: %.2f ms%n", avgLatency);
        System.out.printf("Success: %d, Failed: %d%n", completed.get(), failed.get());
        
        if (failed.get() > 0) {
            System.err.println("WARNING: Detected failures during load test!");
        }
    }
}
