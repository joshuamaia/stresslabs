package br.com.joshua.stresslabsservice.metrics.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "metric_snapshot")
class MetricSnapshotJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "execution_id", nullable = false)
    UUID executionId;

    @Column(name = "captured_at", nullable = false)
    Instant capturedAt;

    @Column(name = "elapsed_ms")
    long elapsedMs;

    double rps;

    @Column(name = "target_rps")
    double targetRps;

    int concurrency;

    @Column(name = "max_concurrency")
    int maxConcurrency;

    @Column(name = "avg_latency_ms")
    double avgLatencyMs;

    @Column(name = "p95_latency_ms")
    double p95LatencyMs;

    @Column(name = "p99_latency_ms")
    double p99LatencyMs;

    long sent;
    long completed;
    long success;
    long errors;
    long blocked;
    long timeouts;

    @Column(name = "connection_failures")
    long connectionFailures;

    @Column(name = "status_2xx")
    long status2xx;

    @Column(name = "status_4xx")
    long status4xx;

    @Column(name = "status_5xx")
    long status5xx;

    double progress;
}
