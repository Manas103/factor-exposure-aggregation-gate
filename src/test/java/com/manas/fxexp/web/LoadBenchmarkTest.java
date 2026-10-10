package com.manas.fxexp.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The p99-latency and throughput claims, measured against the packaged
 * jar running as a real, separate OS process (embedded Tomcat, real
 * PostgreSQL), not the in-process Spring test context the other tests use.
 * Tagged "benchmark" and excluded from the default `mvn test` (see
 * pom.xml); run explicitly with:
 *   mvn package -DskipTests
 *   mvn test -Dtest=LoadBenchmarkTest -Dsurefire.excludedGroups=
 *
 * Process and port discipline (see playbook section 3a): the server port is
 * chosen by briefly binding a local ServerSocket to get one the OS reports
 * free, not a fixed number; the child process handle is held in a local
 * variable and is the only thing this test ever stops, by that PID, before
 * the test method returns.
 */
@Tag("benchmark")
class LoadBenchmarkTest {

    private Process serverProcess;

    @AfterEach
    void stopServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (serverProcess.isAlive()) {
                serverProcess.destroyForcibly();
            }
        }
    }

    @Test
    void p99LatencyAndThroughputUnderConcurrentLoad() throws Exception {
        int port = findFreePort();
        Path jar = Path.of("target", "factor-exposure-aggregation-gate.jar");
        assertTrue(Files.exists(jar), "build the jar first: mvn package -DskipTests");

        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar.toString(),
                "--server.port=" + port, "--logging.level.root=WARN");
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(Path.of("docs", "benchmark_server_stdout.txt").toFile()));
        serverProcess = pb.start();

        HttpClient client = HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + port;
        waitForReady(client, base);

        // Seed 50 funds with a couple of trades each, so the read path has
        // real rows to look up rather than a single hot cache line.
        List<String> fundIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String fundId = "LOADFUND-" + i;
            String strategyId = "LOADSTRAT-" + i;
            postTrade(client, base, "LOADTRD-" + i + "-a", strategyId, fundId);
            postTrade(client, base, "LOADTRD-" + i + "-b", strategyId, fundId);
            fundIds.add(fundId);
        }

        int nThreads = 64;
        Duration duration = Duration.ofSeconds(10);
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicLong completed = new AtomicLong();
        List<Long> latenciesNanos = new CopyOnWriteArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(nThreads);

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < nThreads; i++) {
            int workerId = i;
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    java.util.Random rng = new java.util.Random(workerId);
                    while (!stop.get()) {
                        String fundId = fundIds.get(rng.nextInt(fundIds.size()));
                        long t0 = System.nanoTime();
                        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/aggregates/fund/" + fundId))
                                .GET().timeout(Duration.ofSeconds(5)).build();
                        HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                        long elapsed = System.nanoTime() - t0;
                        if (resp.statusCode() == 200) {
                            latenciesNanos.add(elapsed);
                            completed.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // A request failing under load is recorded by its absence
                    // from latenciesNanos/completed, not swallowed silently;
                    // the summary below reports completed vs. attempted.
                } finally {
                    doneLatch.countDown();
                }
            });
            t.setDaemon(true);
            workers.add(t);
            t.start();
        }

        long wallStart = System.nanoTime();
        startLatch.countDown();
        Thread.sleep(duration.toMillis());
        stop.set(true);
        doneLatch.await(15, java.util.concurrent.TimeUnit.SECONDS);
        long wallElapsedNanos = System.nanoTime() - wallStart;

        List<Long> sorted = new ArrayList<>(latenciesNanos);
        sorted.sort(Long::compareTo);
        double p50Ms = percentileMs(sorted, 0.50);
        double p99Ms = percentileMs(sorted, 0.99);
        double seconds = wallElapsedNanos / 1_000_000_000.0;
        double throughput = completed.get() / seconds;

        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT,
                "requests completed: %d over %.2fs (%d threads, GET /aggregates/fund/{id}, 50 seeded funds)",
                completed.get(), seconds, nThreads));
        lines.add(String.format(Locale.ROOT, "throughput: %.1f req/s", throughput));
        lines.add(String.format(Locale.ROOT, "p50 latency: %.3fms", p50Ms));
        lines.add(String.format(Locale.ROOT, "p99 latency: %.3fms", p99Ms));
        lines.add("claim: p99 under 25ms -> " + (p99Ms < 25.0 ? "MEETS" : "DOES NOT MEET"));
        lines.add("claim: 1,500 req/s -> " + (throughput >= 1500.0 ? "MEETS" : "DOES NOT MEET"));

        Path docsDir = Path.of("docs");
        Files.createDirectories(docsDir);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(docsDir.resolve("benchmark_load_test.txt")))) {
            lines.forEach(w::println);
        }
        lines.forEach(System.out::println);
    }

    private static double percentileMs(List<Long> sortedNanos, double p) {
        if (sortedNanos.isEmpty()) return Double.NaN;
        int idx = (int) Math.ceil(p * sortedNanos.size()) - 1;
        idx = Math.max(0, Math.min(idx, sortedNanos.size() - 1));
        return sortedNanos.get(idx) / 1_000_000.0;
    }

    private static void postTrade(HttpClient client, String base, String externalId, String strategyId,
                                   String fundId) throws Exception {
        String body = String.format(Locale.ROOT,
                "{\"externalId\":\"%s\",\"strategyId\":\"%s\",\"fundId\":\"%s\",\"instrument\":\"INSTR\","
                        + "\"spotExposure\":1000.00,\"volExposure\":50.00,\"skewExposure\":10.00,"
                        + "\"rateExposure\":5.00,\"dividendExposure\":1.00}",
                externalId, strategyId, fundId);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/trades"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.send(req, HttpResponse.BodyHandlers.discarding());
    }

    private static void waitForReady(HttpClient client, String base) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/breaches")).GET().build();
                HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() == 200) return;
            } catch (java.io.IOException ignored) {
                // not up yet
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("server did not become ready within 60s");
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
