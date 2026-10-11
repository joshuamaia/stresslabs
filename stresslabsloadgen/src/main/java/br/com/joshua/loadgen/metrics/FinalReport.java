package br.com.joshua.loadgen.metrics;

/** Resultado consolidado enviado ao backend ao término do teste. */
public record FinalReport(
        String status,
        String failureReason,
        long totalRequests,
        long completed,
        long success,
        long blocked,
        long httpErrors,
        long connectionFailures,
        long timeouts,
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
        int maxInFlight,
        long durationMs) {
}
