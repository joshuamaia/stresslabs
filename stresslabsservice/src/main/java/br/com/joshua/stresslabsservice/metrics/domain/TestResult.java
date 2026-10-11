package br.com.joshua.stresslabsservice.metrics.domain;

import java.util.UUID;

/**
 * Resultado consolidado de uma execução.
 * errorCount = erros HTTP (exceto 429) + falhas de conexão + timeouts; bloqueios (429) são contados à parte.
 */
public record TestResult(
        UUID executionId,
        long totalRequests,
        long completedRequests,
        long successCount,
        long errorCount,
        long blockedCount,
        long httpErrorCount,
        long connectionFailures,
        long timeoutCount,
        long status2xx,
        long status3xx,
        long status4xx,
        long status5xx,
        double avgRps,
        double avgLatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        double minLatencyMs,
        double maxLatencyMs,
        int maxConcurrency,
        long durationMs) {

    /** Percentual de requisições enviadas que obtiveram resposta 2xx/3xx. */
    public double successRate() {
        return totalRequests == 0 ? 0 : successCount * 100.0 / totalRequests;
    }

    public double blockedRate() {
        return totalRequests == 0 ? 0 : blockedCount * 100.0 / totalRequests;
    }
}
