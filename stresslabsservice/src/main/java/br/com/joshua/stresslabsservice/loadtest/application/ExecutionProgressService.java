package br.com.joshua.stresslabsservice.loadtest.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.joshua.stresslabsservice.loadtest.application.port.out.LiveUpdatePublisherPort;
import br.com.joshua.stresslabsservice.loadtest.application.port.out.LoadGeneratorPort;
import br.com.joshua.stresslabsservice.loadtest.application.port.out.TestExecutionRepositoryPort;
import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.loadtest.domain.TestStatus;
import br.com.joshua.stresslabsservice.metrics.application.port.out.MetricsRepositoryPort;
import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;
import br.com.joshua.stresslabsservice.metrics.domain.TestResult;
import br.com.joshua.stresslabsservice.shared.domain.ResourceNotFoundException;

/** Casos de uso: receber progresso/resultado do gerador e encerrar execuções órfãs. */
@Service
public class ExecutionProgressService {

    public static final Duration STALE_GRACE = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(ExecutionProgressService.class);

    private final TestExecutionRepositoryPort executions;
    private final MetricsRepositoryPort metrics;
    private final LiveUpdatePublisherPort publisher;
    private final LoadGeneratorPort loadGenerator;
    private final Clock clock;

    public ExecutionProgressService(TestExecutionRepositoryPort executions, MetricsRepositoryPort metrics,
                                    LiveUpdatePublisherPort publisher, LoadGeneratorPort loadGenerator, Clock clock) {
        this.executions = executions;
        this.metrics = metrics;
        this.publisher = publisher;
        this.loadGenerator = loadGenerator;
        this.clock = clock;
    }

    /** Snapshots de execuções já encerradas são descartados. Retorna false nesse caso. */
    public synchronized boolean recordSnapshot(MetricSnapshot snapshot) {
        TestExecution execution = find(snapshot.executionId());
        if (!execution.status().isActive()) {
            return false;
        }
        if (execution.status() == TestStatus.PENDING) {
            execution.markRunning(clock.instant());
            executions.save(execution);
            publisher.publishStatus(execution);
        }
        metrics.saveSnapshot(snapshot);
        publisher.publishSnapshot(snapshot);
        return true;
    }

    /** Persiste o resultado consolidado e finaliza a execução. Idempotente. */
    public synchronized boolean recordResult(TestResult result, TestStatus finalStatus, String reason) {
        TestExecution execution = find(result.executionId());
        if (!execution.finish(finalStatus, reason, clock.instant())) {
            return false;
        }
        metrics.saveResult(result);
        executions.save(execution);
        publisher.publishResult(result);
        publisher.publishStatus(execution);
        log.info("Teste {} finalizado com status {} ({} requisições)", execution.id(), finalStatus,
                result.totalRequests());
        return true;
    }

    /** Relê a execução: o gerador pode ter enviado progresso/resultado antes desta chamada. */
    public synchronized TestExecution markRunning(UUID id) {
        TestExecution execution = find(id);
        if (execution.status() == TestStatus.PENDING) {
            execution.markRunning(clock.instant());
            executions.save(execution);
            publisher.publishStatus(execution);
        }
        return execution;
    }

    /** Encerra sem resultado consolidado (falha ao iniciar, cancelamento não confirmado). */
    public synchronized TestExecution finishWithoutResult(UUID id, TestStatus finalStatus, String reason) {
        TestExecution execution = find(id);
        if (execution.finish(finalStatus, reason, clock.instant())) {
            executions.save(execution);
            publisher.publishStatus(execution);
        }
        return execution;
    }

    /** Marca como FAILED execuções que excederam duração + tolerância sem resultado (ex.: gerador caiu). */
    public synchronized void failStaleExecutions() {
        executions.findActive()
                .filter(execution -> execution.isStale(clock.instant(), STALE_GRACE))
                .ifPresent(execution -> fail(execution, "Sem resultado do gerador de carga dentro do prazo"));
    }

    /** Ao reiniciar o backend, nenhuma execução anterior pode continuar ativa. */
    public synchronized void recoverAfterRestart() {
        executions.findActive().ifPresent(execution -> fail(execution, "Backend reiniciado durante a execução"));
    }

    private void fail(TestExecution execution, String reason) {
        try {
            loadGenerator.cancel(execution.id());
        } catch (RuntimeException e) {
            log.debug("Cancelamento no gerador falhou: {}", e.getMessage());
        }
        if (execution.finish(TestStatus.FAILED, reason, clock.instant())) {
            executions.save(execution);
            publisher.publishStatus(execution);
            log.warn("Teste {} marcado como FAILED: {}", execution.id(), reason);
        }
    }

    private TestExecution find(UUID id) {
        return executions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execução " + id + " não encontrada"));
    }
}
