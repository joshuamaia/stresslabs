package br.com.joshua.stresslabsservice.loadtest.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import br.com.joshua.stresslabsservice.shared.domain.ConflictException;

/** Agregado que representa uma execução de teste e controla suas transições de status. */
public class TestExecution {

    private final UUID id;
    private final LoadTestConfig config;
    private final String rateLimitSummary;
    private final Instant createdAt;
    private TestStatus status;
    private String failureReason;
    private Instant startedAt;
    private Instant finishedAt;

    public TestExecution(UUID id, LoadTestConfig config, String rateLimitSummary, Instant createdAt,
                         TestStatus status, String failureReason, Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.config = config;
        this.rateLimitSummary = rateLimitSummary;
        this.createdAt = createdAt;
        this.status = status;
        this.failureReason = failureReason;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static TestExecution create(LoadTestConfig config, String rateLimitSummary, Instant now) {
        return new TestExecution(UUID.randomUUID(), config, rateLimitSummary, now, TestStatus.PENDING, null, null,
                null);
    }

    public void markRunning(Instant now) {
        if (status == TestStatus.RUNNING) {
            return;
        }
        if (status != TestStatus.PENDING) {
            throw new ConflictException("Execução " + id + " não pode iniciar a partir do status " + status);
        }
        status = TestStatus.RUNNING;
        startedAt = now;
    }

    /** Encerra a execução. Idempotente: retorna false se já estava finalizada. */
    public boolean finish(TestStatus finalStatus, String reason, Instant now) {
        if (!finalStatus.isFinal()) {
            throw new IllegalArgumentException("Status final inválido: " + finalStatus);
        }
        if (status.isFinal()) {
            return false;
        }
        status = finalStatus;
        failureReason = reason == null ? null : truncate(reason, 500);
        finishedAt = now;
        if (startedAt == null) {
            startedAt = now;
        }
        return true;
    }

    /** Execução ativa que ultrapassou a duração máxima configurada mais uma margem de tolerância. */
    public boolean isStale(Instant now, Duration grace) {
        if (!status.isActive()) {
            return false;
        }
        Instant reference = startedAt != null ? startedAt : createdAt;
        return reference.plusSeconds(config.durationSeconds()).plus(grace).isBefore(now);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID id() {
        return id;
    }

    public LoadTestConfig config() {
        return config;
    }

    public String rateLimitSummary() {
        return rateLimitSummary;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public TestStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }
}
