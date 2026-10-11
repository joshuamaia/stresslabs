package br.com.joshua.loadgen.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import br.com.joshua.loadgen.metrics.FinalReport;
import br.com.joshua.loadgen.metrics.Snapshot;
import br.com.joshua.loadgen.security.TargetGuard;

/** Executa requisições HTTP reais contra um servidor local controlado. */
class LoadTestRunIntegrationTest {

    private HttpServer server;
    private TargetGuard guard;
    private final AtomicInteger concurrent = new AtomicInteger();
    private final AtomicInteger maxConcurrent = new AtomicInteger();
    private final AtomicInteger received = new AtomicInteger();
    private volatile int delayMs;
    private volatile boolean everySecondBlocked;
    private final RecordingListener listener = new RecordingListener();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/target/", this::handle);
        server.start();
        guard = new TargetGuard(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                List.of("/api/target/"));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        int current = concurrent.incrementAndGet();
        maxConcurrent.accumulateAndGet(current, Math::max);
        int number = received.incrementAndGet();
        try {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            int status = everySecondBlocked && number % 2 == 0 ? 429 : 200;
            byte[] body = "{\"ok\":true}".getBytes();
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // cliente desistiu (timeout/cancelamento)
        } finally {
            concurrent.decrementAndGet();
            exchange.close();
        }
    }

    private LoadTestRun run(Scenario scenario, int total, int concurrency, int rps, int durationSeconds,
                            int timeoutMs) {
        var plan = new RunPlan(UUID.randomUUID(), scenario, "GET", guard.validatePath("/api/target/health"), 0, null,
                Map.of(), total, concurrency, rps, durationSeconds, timeoutMs);
        return new LoadTestRun(plan, new HttpRequestExecutor(plan, guard), listener, Duration.ofMillis(200),
                Clock.systemUTC());
    }

    @Test
    void executesAllRequestsAndReportsSuccess() {
        FinalReport report = run(Scenario.CONSTANT, 50, 10, 1000, 10, 2000).run();

        assertThat(report.status()).isEqualTo("COMPLETED");
        assertThat(report.totalRequests()).isEqualTo(50);
        assertThat(report.completed()).isEqualTo(50);
        assertThat(report.success()).isEqualTo(50);
        assertThat(report.status2xx()).isEqualTo(50);
        assertThat(received.get()).isEqualTo(50);
        assertThat(report.avgLatencyMs()).isPositive();
        assertThat(report.p99LatencyMs()).isGreaterThanOrEqualTo(report.p95LatencyMs());
        assertThat(listener.finished).hasSize(1);
        assertThat(listener.snapshots).isNotEmpty();
        assertThat(listener.snapshots.getLast().progress()).isEqualTo(1.0);
    }

    @Test
    void neverExceedsConfiguredConcurrency() {
        delayMs = 100;
        FinalReport report = run(Scenario.CONSTANT, 30, 3, 1000, 10, 2000).run();

        assertThat(report.completed()).isEqualTo(30);
        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(3);
        assertThat(report.maxInFlight()).isLessThanOrEqualTo(3).isPositive();
    }

    @Test
    void controlsTheSendRate() {
        FinalReport report = run(Scenario.CONSTANT, 10_000, 20, 50, 1, 2000).run();

        // 1s a 50 rps: ~50 requisições (tolerância para escalonamento do SO)
        assertThat(report.totalRequests()).isBetween(40L, 60L);
        assertThat(report.status()).isEqualTo("COMPLETED");
    }

    @Test
    void countsHttp429AsBlocked() {
        everySecondBlocked = true;
        FinalReport report = run(Scenario.RATE_LIMIT, 20, 1, 1000, 10, 2000).run();

        assertThat(report.blocked()).isEqualTo(10);
        assertThat(report.success()).isEqualTo(10);
        assertThat(report.httpErrors()).isZero();
        assertThat(report.status4xx()).isEqualTo(10);
    }

    @Test
    void countsTimeoutsSeparately() {
        delayMs = 1_000;
        FinalReport report = run(Scenario.CONSTANT, 3, 3, 1000, 10, 150).run();

        assertThat(report.timeouts()).isEqualTo(3);
        assertThat(report.completed()).isZero();
        assertThat(report.success()).isZero();
    }

    @Test
    void cancellationStopsTheRunAndReportsPartialResults() throws Exception {
        delayMs = 50;
        LoadTestRun run = run(Scenario.CONSTANT, 100_000, 5, 50, 60, 2000);
        Thread thread = Thread.ofVirtual().start(run::run);
        Thread.sleep(500);
        run.cancel();
        assertThat(run.awaitFinished(Duration.ofSeconds(10))).isTrue();
        thread.join();

        FinalReport report = run.report();
        assertThat(report.status()).isEqualTo("STOPPED");
        assertThat(report.totalRequests()).isLessThan(100);
        assertThat(report.durationMs()).isLessThan(10_000);
    }

    @Test
    void countsConnectionFailures() throws IOException {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var closedGuard = new TargetGuard(URI.create("http://127.0.0.1:" + closedPort), List.of("/api/target/"));
        var plan = new RunPlan(UUID.randomUUID(), Scenario.CONSTANT, "GET", "/api/target/health", 0, null, Map.of(),
                5, 5, 1000, 10, 1000);
        FinalReport report = new LoadTestRun(plan, new HttpRequestExecutor(plan, closedGuard), listener,
                Duration.ofMillis(200), Clock.systemUTC()).run();

        assertThat(report.connectionFailures()).isEqualTo(5);
        assertThat(report.completed()).isZero();
    }

    static class RecordingListener implements ProgressListener {
        final List<Snapshot> snapshots = new CopyOnWriteArrayList<>();
        final List<FinalReport> finished = new CopyOnWriteArrayList<>();

        @Override
        public boolean onSnapshot(UUID executionId, Snapshot snapshot) {
            snapshots.add(snapshot);
            return true;
        }

        @Override
        public void onFinished(UUID executionId, FinalReport report) {
            finished.add(report);
        }
    }
}
