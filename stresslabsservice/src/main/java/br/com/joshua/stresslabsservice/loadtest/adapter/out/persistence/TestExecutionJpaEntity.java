package br.com.joshua.stresslabsservice.loadtest.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import br.com.joshua.stresslabsservice.loadtest.domain.TargetHttpMethod;
import br.com.joshua.stresslabsservice.loadtest.domain.TestScenario;
import br.com.joshua.stresslabsservice.loadtest.domain.TestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_execution")
class TestExecutionJpaEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 120)
    String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    TestScenario scenario;

    @Column(name = "target_key", nullable = false, length = 40)
    String targetKey;

    @Column(nullable = false)
    String endpoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false, length = 10)
    TargetHttpMethod httpMethod;

    @Column(name = "total_requests")
    int totalRequests;

    int concurrency;

    @Column(name = "target_rps")
    int targetRps;

    @Column(name = "duration_seconds")
    int durationSeconds;

    @Column(name = "timeout_ms")
    int timeoutMs;

    @Column(name = "rate_limit_enabled")
    boolean rateLimitEnabled;

    @Column(name = "rate_limit_summary", length = 120)
    String rateLimitSummary;

    @Column(name = "request_body")
    String requestBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    TestStatus status;

    @Column(name = "failure_reason", length = 500)
    String failureReason;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;
}
