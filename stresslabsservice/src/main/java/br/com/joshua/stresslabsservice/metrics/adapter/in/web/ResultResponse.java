package br.com.joshua.stresslabsservice.metrics.adapter.in.web;

import java.util.UUID;

import br.com.joshua.stresslabsservice.metrics.domain.TestResult;

public record ResultResponse(
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
        long durationMs,
        double successRate,
        double blockedRate) {

    public static ResultResponse from(TestResult r) {
        if (r == null) {
            return null;
        }
        return new ResultResponse(r.executionId(), r.totalRequests(), r.completedRequests(), r.successCount(),
                r.errorCount(), r.blockedCount(), r.httpErrorCount(), r.connectionFailures(), r.timeoutCount(),
                r.status2xx(), r.status3xx(), r.status4xx(), r.status5xx(), r.avgRps(), r.avgLatencyMs(),
                r.p95LatencyMs(), r.p99LatencyMs(), r.minLatencyMs(), r.maxLatencyMs(), r.maxConcurrency(),
                r.durationMs(), r.successRate(), r.blockedRate());
    }
}
