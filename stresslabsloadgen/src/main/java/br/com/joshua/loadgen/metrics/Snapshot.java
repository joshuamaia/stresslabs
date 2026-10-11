package br.com.joshua.loadgen.metrics;

import java.time.Instant;

/** Métricas da janela atual (rps, latências, maxInFlight) e contadores acumulados. */
public record Snapshot(
        Instant timestamp,
        long elapsedMs,
        double currentRps,
        double targetRps,
        int inFlight,
        int maxInFlight,
        double avgLatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        long sent,
        long completed,
        long success,
        long blocked,
        long httpErrors,
        long connectionFailures,
        long timeouts,
        long status2xx,
        long status4xx,
        long status5xx,
        double progress) {
}
