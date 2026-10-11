package br.com.joshua.loadgen.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class MetricsCollectorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final long MS = 1_000_000;

    @Test
    void classifiesResponsesAndFailures() {
        var metrics = new MetricsCollector(10, CLOCK, 0);
        for (int i = 0; i < 7; i++) {
            metrics.onSent();
        }
        metrics.onResponse(200, 10 * MS);
        metrics.onResponse(201, 20 * MS);
        metrics.onResponse(302, 5 * MS);
        metrics.onResponse(429, 1 * MS);
        metrics.onResponse(404, 2 * MS);
        metrics.onResponse(503, 3 * MS);
        metrics.onFailure(Outcome.TIMEOUT);

        FinalReport report = metrics.finalReport("COMPLETED", null, 1_000 * MS);

        assertThat(report.totalRequests()).isEqualTo(7);
        assertThat(report.completed()).isEqualTo(6);
        assertThat(report.success()).isEqualTo(3);
        assertThat(report.blocked()).isEqualTo(1);
        assertThat(report.httpErrors()).isEqualTo(2);
        assertThat(report.timeouts()).isEqualTo(1);
        assertThat(report.status2xx()).isEqualTo(2);
        assertThat(report.status3xx()).isEqualTo(1);
        assertThat(report.status4xx()).isEqualTo(2);
        assertThat(report.status5xx()).isEqualTo(1);
        assertThat(report.avgRps()).isEqualTo(6.0);
        assertThat(report.durationMs()).isEqualTo(1_000);
        assertThat(report.maxLatencyMs()).isEqualTo(20.0);
    }

    @Test
    void tracksPeakConcurrencyPerWindowAndGlobally() {
        var metrics = new MetricsCollector(10, CLOCK, 0);
        metrics.onRequestStart();
        metrics.onRequestStart();
        metrics.onRequestStart();
        metrics.onRequestEnd();
        metrics.onRequestEnd();

        Snapshot first = metrics.snapshot(1_000 * MS, 0, 10, 0.5);
        assertThat(first.inFlight()).isEqualTo(1);
        assertThat(first.maxInFlight()).isEqualTo(3);

        Snapshot second = metrics.snapshot(2_000 * MS, 0, 10, 0.6);
        assertThat(second.maxInFlight()).isEqualTo(1);
        assertThat(metrics.finalReport("COMPLETED", null, 2_000 * MS).maxInFlight()).isEqualTo(3);
    }

    @Test
    void snapshotUsesOnlyTheCurrentWindowForRpsAndLatency() {
        var metrics = new MetricsCollector(10, CLOCK, 0);
        metrics.onResponse(200, 100 * MS);
        metrics.onResponse(200, 100 * MS);
        Snapshot first = metrics.snapshot(1_000 * MS, 0, 2, 0.5);
        assertThat(first.currentRps()).isEqualTo(2.0);
        assertThat(first.avgLatencyMs()).isEqualTo(100.0);

        metrics.onResponse(200, 10 * MS);
        Snapshot second = metrics.snapshot(1_500 * MS, 0, 2, 0.7);
        assertThat(second.currentRps()).isEqualTo(2.0); // 1 resposta em 0,5s
        assertThat(second.avgLatencyMs()).isEqualTo(10.0);
        assertThat(second.completed()).isEqualTo(3);
        assertThat(second.elapsedMs()).isEqualTo(1_500);
    }
}
