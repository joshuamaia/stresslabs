package br.com.joshua.stresslabsservice.loadtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import br.com.joshua.stresslabsservice.shared.domain.ConflictException;

class TestExecutionTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static TestExecution newExecution() {
        LoadTestConfig config = LoadTestConfigTest.create(LoadTestConfigTest.HEALTH, null, 100, 10, 10, 30, 1000,
                null);
        return TestExecution.create(config, "summary", NOW);
    }

    @Test
    void followsTheLifecycle() {
        TestExecution execution = newExecution();
        assertThat(execution.status()).isEqualTo(TestStatus.PENDING);

        execution.markRunning(NOW.plusSeconds(1));
        assertThat(execution.status()).isEqualTo(TestStatus.RUNNING);
        assertThat(execution.startedAt()).isEqualTo(NOW.plusSeconds(1));

        assertThat(execution.finish(TestStatus.COMPLETED, null, NOW.plusSeconds(10))).isTrue();
        assertThat(execution.status()).isEqualTo(TestStatus.COMPLETED);
        assertThat(execution.finishedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void finishIsIdempotent() {
        TestExecution execution = newExecution();
        execution.finish(TestStatus.STOPPED, "stop", NOW);
        assertThat(execution.finish(TestStatus.COMPLETED, null, NOW.plusSeconds(5))).isFalse();
        assertThat(execution.status()).isEqualTo(TestStatus.STOPPED);
    }

    @Test
    void cannotRestartAFinishedExecution() {
        TestExecution execution = newExecution();
        execution.finish(TestStatus.FAILED, "erro", NOW);
        assertThatThrownBy(() -> execution.markRunning(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void detectsStaleExecutions() {
        TestExecution execution = newExecution();
        execution.markRunning(NOW);
        Duration grace = Duration.ofSeconds(60);
        assertThat(execution.isStale(NOW.plusSeconds(89), grace)).isFalse();
        assertThat(execution.isStale(NOW.plusSeconds(91), grace)).isTrue();
    }
}
