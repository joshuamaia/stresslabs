package br.com.joshua.stresslabsservice.metrics.domain;

import java.time.Instant;

/** Métricas da JVM/servidor da API (Micrometer), separadas das métricas do gerador de carga. */
public record InfrastructureMetrics(
        Instant capturedAt,
        Double processCpuUsage,
        Double systemCpuUsage,
        Integer availableProcessors,
        Double heapUsedBytes,
        Double heapMaxBytes,
        Double nonHeapUsedBytes,
        Double liveThreads,
        Double peakThreads,
        Double daemonThreads,
        Long httpRequestCount,
        Double httpMeanLatencyMs,
        Double httpMaxLatencyMs,
        Double dbActiveConnections,
        Double dbIdleConnections,
        Double dbPendingConnections,
        Double dbMaxConnections,
        Double uptimeSeconds) {
}
