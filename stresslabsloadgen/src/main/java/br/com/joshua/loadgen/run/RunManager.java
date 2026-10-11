package br.com.joshua.loadgen.run;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.joshua.loadgen.config.GeneratorProperties;
import br.com.joshua.loadgen.execution.HttpRequestExecutor;
import br.com.joshua.loadgen.execution.LoadTestRun;
import br.com.joshua.loadgen.execution.ProgressListener;
import br.com.joshua.loadgen.execution.RunPlan;
import br.com.joshua.loadgen.security.TargetGuard;
import jakarta.annotation.PreDestroy;

/** Garante no máximo uma execução ativa por vez e controla seu ciclo de vida. */
@Component
public class RunManager {

    private static final Logger log = LoggerFactory.getLogger(RunManager.class);

    private final AtomicReference<LoadTestRun> active = new AtomicReference<>();
    private final TargetGuard guard;
    private final ProgressListener listener;
    private final GeneratorProperties properties;
    private final Clock clock;

    public RunManager(TargetGuard guard, ProgressListener listener, GeneratorProperties properties, Clock clock) {
        this.guard = guard;
        this.listener = listener;
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized LoadTestRun start(RunPlan plan) {
        LoadTestRun current = active.get();
        if (current != null && !current.isFinished()) {
            throw new RunConflictException("Já existe uma execução ativa: " + current.executionId());
        }
        var run = new LoadTestRun(plan, new HttpRequestExecutor(plan, guard), listener,
                properties.snapshotInterval(), clock);
        active.set(run);
        Thread.ofVirtual().name("load-run-" + plan.executionId()).start(run::run);
        log.info("Execução {} iniciada: {} {} ({} req, concorrência {}, {} rps, {}s)", plan.executionId(),
                plan.scenario(), plan.path(), plan.totalRequests(), plan.concurrency(), plan.targetRps(),
                plan.durationSeconds());
        return run;
    }

    public boolean cancel(UUID executionId) {
        LoadTestRun current = active.get();
        if (current == null || !current.executionId().equals(executionId) || current.isFinished()) {
            return false;
        }
        current.cancel();
        log.info("Cancelamento solicitado para {}", executionId);
        return true;
    }

    public Optional<LoadTestRun> active() {
        return Optional.ofNullable(active.get()).filter(run -> !run.isFinished());
    }

    /** Encerramento seguro: cancela a execução ativa e aguarda o envio do resultado parcial. */
    @PreDestroy
    void shutdown() throws InterruptedException {
        LoadTestRun current = active.get();
        if (current != null && !current.isFinished()) {
            current.cancel();
            current.awaitFinished(Duration.ofSeconds(15));
        }
    }
}
