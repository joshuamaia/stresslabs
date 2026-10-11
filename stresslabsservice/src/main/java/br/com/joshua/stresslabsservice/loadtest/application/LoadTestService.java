package br.com.joshua.stresslabsservice.loadtest.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.joshua.stresslabsservice.loadtest.application.port.out.LiveUpdatePublisherPort;
import br.com.joshua.stresslabsservice.loadtest.application.port.out.LoadGeneratorPort;
import br.com.joshua.stresslabsservice.loadtest.application.port.out.TestExecutionRepositoryPort;
import br.com.joshua.stresslabsservice.loadtest.domain.AllowedTarget;
import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestConfig;
import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestLimits;
import br.com.joshua.stresslabsservice.loadtest.domain.TargetCatalog;
import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.loadtest.domain.TestScenario;
import br.com.joshua.stresslabsservice.loadtest.domain.TestStatus;
import br.com.joshua.stresslabsservice.ratelimit.application.RateLimitService;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitPolicy;
import br.com.joshua.stresslabsservice.shared.domain.ConflictException;
import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;
import br.com.joshua.stresslabsservice.shared.domain.ExternalServiceException;
import br.com.joshua.stresslabsservice.shared.domain.ResourceNotFoundException;

/** Casos de uso: iniciar, interromper e consultar execuções de teste. */
@Service
public class LoadTestService {

    private static final Logger log = LoggerFactory.getLogger(LoadTestService.class);

    private final TestExecutionRepositoryPort executions;
    private final LoadGeneratorPort loadGenerator;
    private final LiveUpdatePublisherPort publisher;
    private final ExecutionProgressService progress;
    private final RateLimitService rateLimitService;
    private final LoadTestLimits limits;
    private final Clock clock;
    /** Impede inícios simultâneos conflitantes (instância única no MVP; o banco também garante via índice). */
    private final ReentrantLock startLock = new ReentrantLock();

    public LoadTestService(TestExecutionRepositoryPort executions, LoadGeneratorPort loadGenerator,
                           LiveUpdatePublisherPort publisher, ExecutionProgressService progress,
                           RateLimitService rateLimitService, LoadTestLimits limits, Clock clock) {
        this.executions = executions;
        this.loadGenerator = loadGenerator;
        this.publisher = publisher;
        this.progress = progress;
        this.rateLimitService = rateLimitService;
        this.limits = limits;
        this.clock = clock;
    }

    public List<AllowedTarget> targets() {
        return TargetCatalog.all();
    }

    public LoadTestLimits limits() {
        return limits;
    }

    public TestExecution start(StartLoadTestCommand command) {
        AllowedTarget target = TargetCatalog.find(command.targetKey())
                .orElseThrow(() -> new DomainValidationException(
                        "Endpoint não autorizado: " + command.targetKey()));
        LoadTestConfig config = LoadTestConfig.create(command.name(), command.scenario(), target, command.method(),
                command.totalRequests(), command.concurrency(), command.targetRps(), command.durationSeconds(),
                command.timeoutMs(), command.rateLimitEnabled(), command.requestBody(), command.headers(), limits);

        startLock.lock();
        try {
            executions.findActive().ifPresent(active -> {
                throw new ConflictException("Já existe um teste em execução: " + active.config().name());
            });
            validateRateLimitScenario(config);

            RateLimitPolicy policy = rateLimitService.setEnabled(config.rateLimitEnabled());
            rateLimitService.resetBuckets();

            TestExecution execution = executions.save(TestExecution.create(config, policy.summary(), clock.instant()));
            publisher.publishStatus(execution);
            try {
                loadGenerator.start(execution);
            } catch (RuntimeException e) {
                progress.finishWithoutResult(execution.id(), TestStatus.FAILED,
                        "Gerador de carga indisponível: " + e.getMessage());
                throw e instanceof ExternalServiceException ext ? ext
                        : new ExternalServiceException("Falha ao iniciar o gerador de carga", e);
            }
            log.info("Teste {} iniciado ({}, {})", execution.id(), config.scenario(), target.key());
            return progress.markRunning(execution.id());
        } finally {
            startLock.unlock();
        }
    }

    public TestExecution stop(UUID id) {
        TestExecution execution = get(id);
        if (!execution.status().isActive()) {
            return execution;
        }
        boolean acknowledged;
        try {
            acknowledged = loadGenerator.cancel(id);
        } catch (RuntimeException e) {
            log.warn("Falha ao cancelar no gerador: {}", e.getMessage());
            acknowledged = false;
        }
        if (!acknowledged) {
            // O gerador não está com o teste: encerra localmente para não ficar preso em execução
            return progress.finishWithoutResult(id, TestStatus.STOPPED, "Interrompido (gerador não confirmou)");
        }
        // Confirmado: o gerador envia o resultado parcial com status STOPPED
        return execution;
    }

    public Optional<TestExecution> active() {
        return executions.findActive();
    }

    public TestExecution get(UUID id) {
        return executions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execução " + id + " não encontrada"));
    }

    private void validateRateLimitScenario(LoadTestConfig config) {
        if (config.scenario() != TestScenario.RATE_LIMIT) {
            return;
        }
        if (!config.rateLimitEnabled()) {
            throw new DomainValidationException("O cenário RATE_LIMIT exige o Rate Limiting habilitado");
        }
        long capacity = rateLimitService.currentPolicy().capacity();
        if (config.totalRequests() <= capacity) {
            throw new DomainValidationException("O cenário RATE_LIMIT precisa de mais requisições ("
                    + config.totalRequests() + ") que a capacidade do bucket (" + capacity + ")");
        }
    }
}
