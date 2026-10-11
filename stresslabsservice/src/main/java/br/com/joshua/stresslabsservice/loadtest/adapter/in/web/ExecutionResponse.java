package br.com.joshua.stresslabsservice.loadtest.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestConfig;
import br.com.joshua.stresslabsservice.loadtest.domain.TargetHttpMethod;
import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.loadtest.domain.TestScenario;
import br.com.joshua.stresslabsservice.loadtest.domain.TestStatus;

public record ExecutionResponse(
        UUID id,
        String name,
        TestScenario scenario,
        String targetKey,
        String endpoint,
        TargetHttpMethod method,
        int totalRequests,
        int concurrency,
        int targetRps,
        int durationSeconds,
        int timeoutMs,
        boolean rateLimitEnabled,
        String rateLimitSummary,
        TestStatus status,
        String failureReason,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    public static ExecutionResponse from(TestExecution execution) {
        LoadTestConfig c = execution.config();
        return new ExecutionResponse(execution.id(), c.name(), c.scenario(), c.target().key(), c.target().path(),
                c.method(), c.totalRequests(), c.concurrency(), c.targetRps(), c.durationSeconds(), c.timeoutMs(),
                c.rateLimitEnabled(), execution.rateLimitSummary(), execution.status(), execution.failureReason(),
                execution.createdAt(), execution.startedAt(), execution.finishedAt());
    }
}
