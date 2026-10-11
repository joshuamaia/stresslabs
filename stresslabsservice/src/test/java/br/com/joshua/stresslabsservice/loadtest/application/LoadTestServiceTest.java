package br.com.joshua.stresslabsservice.loadtest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.com.joshua.stresslabsservice.loadtest.application.port.out.LiveUpdatePublisherPort;
import br.com.joshua.stresslabsservice.loadtest.application.port.out.LoadGeneratorPort;
import br.com.joshua.stresslabsservice.loadtest.application.port.out.TestExecutionRepositoryPort;
import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestLimits;
import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.loadtest.domain.TestScenario;
import br.com.joshua.stresslabsservice.loadtest.domain.TestStatus;
import br.com.joshua.stresslabsservice.metrics.application.port.out.MetricsRepositoryPort;
import br.com.joshua.stresslabsservice.ratelimit.application.RateLimitService;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitPolicy;
import br.com.joshua.stresslabsservice.shared.domain.ConflictException;
import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;
import br.com.joshua.stresslabsservice.shared.domain.ExternalServiceException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoadTestServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final LoadTestLimits LIMITS = new LoadTestLimits(10_000, 100, 60, 1000, 100, 10_000);

    @Mock
    private LoadGeneratorPort generator;
    @Mock
    private LiveUpdatePublisherPort publisher;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private MetricsRepositoryPort metrics;
    private final InMemoryExecutions executions = new InMemoryExecutions();
    private LoadTestService service;

    @BeforeEach
    void setUp() {
        var progress = new ExecutionProgressService(executions, metrics, publisher, generator, CLOCK);
        service = new LoadTestService(executions, generator, publisher, progress, rateLimitService, LIMITS, CLOCK);
        RateLimitPolicy policy = RateLimitPolicy.defaults(CLOCK.instant());
        when(rateLimitService.currentPolicy()).thenReturn(policy);
        when(rateLimitService.setEnabled(any(Boolean.class))).thenAnswer(
                invocation -> policy.withEnabled(invocation.getArgument(0), CLOCK.instant()));
    }

    private static StartLoadTestCommand command(TestScenario scenario, int total, boolean rateLimit) {
        return new StartLoadTestCommand("teste", scenario, "health", null, total, 10, 100, 10, 1000, rateLimit, null,
                null);
    }

    @Test
    void startsTestAppliesRateLimitSettingAndMarksRunning() {
        TestExecution execution = service.start(command(TestScenario.CONSTANT, 100, false));

        assertThat(execution.status()).isEqualTo(TestStatus.RUNNING);
        assertThat(execution.rateLimitSummary()).isEqualTo("Desabilitado");
        verify(rateLimitService).setEnabled(false);
        verify(rateLimitService).resetBuckets();
        verify(generator).start(any());
    }

    @Test
    void rejectsConcurrentConflictingExecutions() {
        service.start(command(TestScenario.CONSTANT, 100, true));

        assertThatThrownBy(() -> service.start(command(TestScenario.CONSTANT, 100, true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsUnknownTargets() {
        var command = new StartLoadTestCommand("x", TestScenario.CONSTANT, "https://evil.com", null, 10, 1, 1, 1,
                1000, false, null, null);
        assertThatThrownBy(() -> service.start(command)).isInstanceOf(DomainValidationException.class);
        verify(generator, never()).start(any());
    }

    @Test
    void rateLimitScenarioRequiresEnabledLimitAndEnoughRequests() {
        assertThatThrownBy(() -> service.start(command(TestScenario.RATE_LIMIT, 500, false)))
                .hasMessageContaining("exige o Rate Limiting");
        assertThatThrownBy(() -> service.start(command(TestScenario.RATE_LIMIT, 100, true)))
                .hasMessageContaining("capacidade");
        assertThat(service.start(command(TestScenario.RATE_LIMIT, 101, true)).status())
                .isEqualTo(TestStatus.RUNNING);
    }

    @Test
    void generatorFailureMarksExecutionAsFailed() {
        doThrow(new ExternalServiceException("down", null)).when(generator).start(any());

        assertThatThrownBy(() -> service.start(command(TestScenario.CONSTANT, 100, true)))
                .isInstanceOf(ExternalServiceException.class);
        TestExecution stored = executions.store.values().iterator().next();
        assertThat(stored.status()).isEqualTo(TestStatus.FAILED);
        assertThat(executions.findActive()).isEmpty();
    }

    @Test
    void stopCancelsOnGeneratorOrFinishesLocally() {
        TestExecution execution = service.start(command(TestScenario.CONSTANT, 100, true));
        when(generator.cancel(execution.id())).thenReturn(true);
        assertThat(service.stop(execution.id()).status()).isEqualTo(TestStatus.RUNNING);

        when(generator.cancel(execution.id())).thenReturn(false);
        assertThat(service.stop(execution.id()).status()).isEqualTo(TestStatus.STOPPED);
        verify(publisher, org.mockito.Mockito.atLeastOnce()).publishStatus(any());
        verify(generator, org.mockito.Mockito.times(2)).cancel(eq(execution.id()));
    }

    /** Repositório em memória que copia o agregado, simulando a persistência real. */
    static class InMemoryExecutions implements TestExecutionRepositoryPort {
        final Map<UUID, TestExecution> store = new HashMap<>();

        private static TestExecution copy(TestExecution e) {
            return new TestExecution(e.id(), e.config(), e.rateLimitSummary(), e.createdAt(), e.status(),
                    e.failureReason(), e.startedAt(), e.finishedAt());
        }

        @Override
        public TestExecution save(TestExecution execution) {
            store.put(execution.id(), copy(execution));
            return execution;
        }

        @Override
        public Optional<TestExecution> findById(UUID id) {
            return Optional.ofNullable(store.get(id)).map(InMemoryExecutions::copy);
        }

        @Override
        public Optional<TestExecution> findActive() {
            return store.values().stream().filter(e -> e.status().isActive()).findFirst()
                    .map(InMemoryExecutions::copy);
        }

        @Override
        public br.com.joshua.stresslabsservice.shared.domain.PageResult<TestExecution> findPage(int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(UUID id) {
            store.remove(id);
        }

        @Override
        public long deleteAllFinished() {
            return 0;
        }
    }
}
