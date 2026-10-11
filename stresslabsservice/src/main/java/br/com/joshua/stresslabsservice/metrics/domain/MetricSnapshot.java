package br.com.joshua.stresslabsservice.metrics.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Agregado periódico (janela de ~1s) produzido pelo gerador de carga.
 * rps/latências referem-se à janela; contadores são acumulados desde o início.
 */
public record MetricSnapshot(
        UUID executionId,
        Instant capturedAt,
        long elapsedMs,
        double rps,
        double targetRps,
        int concurrency,
        int maxConcurrency,
        double avgLatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        long sent,
        long completed,
        long success,
        long errors,
        long blocked,
        long timeouts,
        long connectionFailures,
        long status2xx,
        long status4xx,
        long status5xx,
        double progress) {
}
