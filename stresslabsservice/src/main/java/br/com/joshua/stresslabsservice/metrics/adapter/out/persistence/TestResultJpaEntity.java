package br.com.joshua.stresslabsservice.metrics.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_result")
class TestResultJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "execution_id", nullable = false, unique = true)
    UUID executionId;

    @Column(name = "total_requests")
    long totalRequests;

    @Column(name = "completed_requests")
    long completedRequests;

    @Column(name = "success_count")
    long successCount;

    @Column(name = "error_count")
    long errorCount;

    @Column(name = "blocked_count")
    long blockedCount;

    @Column(name = "http_error_count")
    long httpErrorCount;

    @Column(name = "connection_failures")
    long connectionFailures;

    @Column(name = "timeout_count")
    long timeoutCount;

    @Column(name = "status_2xx")
    long status2xx;

    @Column(name = "status_3xx")
    long status3xx;

    @Column(name = "status_4xx")
    long status4xx;

    @Column(name = "status_5xx")
    long status5xx;

    @Column(name = "avg_rps")
    double avgRps;

    @Column(name = "avg_latency_ms")
    double avgLatencyMs;

    @Column(name = "p95_latency_ms")
    double p95LatencyMs;

    @Column(name = "p99_latency_ms")
    double p99LatencyMs;

    @Column(name = "min_latency_ms")
    double minLatencyMs;

    @Column(name = "max_latency_ms")
    double maxLatencyMs;

    @Column(name = "max_concurrency")
    int maxConcurrency;

    @Column(name = "duration_ms")
    long durationMs;
}
