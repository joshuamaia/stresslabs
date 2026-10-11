package br.com.joshua.stresslabsservice.loadtest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.joshua.stresslabsservice.loadtest.application.LoadTestServiceTest.InMemoryExecutions;
import br.com.joshua.stresslabsservice.loadtest.application.port.out.LiveUpdatePublisherPort;
import br.com.joshua.stresslabsservice.loadtest.application.port.out.LoadGeneratorPort;
import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestConfig;
import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestLimits;
import br.com.joshua.stresslabsservice.loadtest.domain.TargetCatalog;
import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.loadtest.domain.TestScenario;
import br.com.joshua.stresslabsservice.loadtest.domain.TestStatus;
import br.com.joshua.stresslabsservice.metrics.application.port.out.MetricsRepositoryPort;
import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;
import br.com.joshua.stresslabsservice.metrics.domain.TestResult;

@ExtendWith(MockitoExtension.class)
class ExecutionProgressServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private MetricsRepositoryPort metrics;
    @Mock
    private LiveUpdatePublisherPort publisher;
    @Mock
    private LoadGeneratorPort generator;
    private final InMemoryExecutions executions = new InMemoryExecutions();

    private ExecutionProgressService service(Instant now) {
        return new ExecutionProgressService(executions, metrics, publisher, generator,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private TestExecution runningExecution() {
        LoadTestConfig config = LoadTestConfig.create("t", TestScenario.CONSTANT,
                TargetCatalog.find("health").orElseThrow(), null, 100, 10, 10, 30, 1000, false, null, null,
                new LoadTestLimits(1000, 100, 300, 1000, 100, 10_000));
        TestExecution execution = TestExecution.create(config, "Desabilitado", START);
        execution.markRunning(START);
        return executions.save(execution);
    }

    private static MetricSnapshot snapshot(UUID id) {
        return new MetricSnapshot(id, START, 1000, 10, 10, 2, 3, 5, 8, 9, 10, 10, 10, 0, 0, 0, 0, 10, 0, 0, 0.1);
    }

    private static TestResult result(UUID id) {
        return new TestResult(id, 100, 100, 90, 0, 10, 0, 0, 0, 90, 0, 10, 0, 10, 5, 8, 9, 1, 20, 5, 10_000);
    }

    @Test
    void persistsAndPublishesSnapshotsWhileRunning() {
        TestExecution execution = runningExecution();
        assertThat(service(START).recordSnapshot(snapshot(execution.id()))).isTrue();
        verify(metrics).saveSnapshot(any());
        verify(publisher).publishSnapshot(any());
    }

    @Test
    void resultIsPersistedOnceAndLateSnapshotsAreRejected() {
        TestExecution execution = runningExecution();
        ExecutionProgressService service = service(START.plusSeconds(10));

        assertThat(service.recordResult(result(execution.id()), TestStatus.COMPLETED, null)).isTrue();
        assertThat(service.recordResult(result(execution.id()), TestStatus.COMPLETED, null)).isFalse();
        assertThat(service.recordSnapshot(snapshot(execution.id()))).isFalse();

        verify(metrics, times(1)).saveResult(any());
        verify(metrics, never()).saveSnapshot(any());
        assertThat(executions.findById(execution.id()).orElseThrow().status()).isEqualTo(TestStatus.COMPLETED);
    }

    @Test
    void staleExecutionsAreFailed() {
        TestExecution execution = runningExecution();

        service(START.plusSeconds(60)).failStaleExecutions();
        assertThat(executions.findById(execution.id()).orElseThrow().status()).isEqualTo(TestStatus.RUNNING);

        service(START.plusSeconds(30 + 61)).failStaleExecutions();
        assertThat(executions.findById(execution.id()).orElseThrow().status()).isEqualTo(TestStatus.FAILED);
        verify(generator).cancel(execution.id());
    }
}
