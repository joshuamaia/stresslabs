package br.com.joshua.loadgen.execution;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.joshua.loadgen.metrics.FinalReport;
import br.com.joshua.loadgen.metrics.MetricsCollector;

/**
 * Uma execução de teste de carga.
 * <ul>
 *   <li>Taxa de envio: um acumulador de créditos segue o {@link RateProfile} do cenário.</li>
 *   <li>Concorrência: um {@link Semaphore} limita as requisições simultâneas; cada requisição roda
 *       em uma Virtual Thread, mas nunca há mais do que {@code concurrency} em andamento.</li>
 *   <li>Parada: total de requisições atingido, duração máxima esgotada ou cancelamento.</li>
 * </ul>
 */
public class LoadTestRun {

    static final Duration DRAIN_GRACE = Duration.ofSeconds(5);
    private static final long MAX_PARK_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
    private static final Logger log = LoggerFactory.getLogger(LoadTestRun.class);

    private final RunPlan plan;
    private final HttpRequestExecutor http;
    private final ProgressListener listener;
    private final Duration snapshotInterval;
    private final Clock clock;
    private final RateProfile profile;
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile boolean cancelRequested;
    private volatile FinalReport report;

    public LoadTestRun(RunPlan plan, HttpRequestExecutor http, ProgressListener listener, Duration snapshotInterval,
                       Clock clock) {
        this.plan = plan;
        this.http = http;
        this.listener = listener;
        this.snapshotInterval = snapshotInterval;
        this.clock = clock;
        this.profile = new RateProfile(plan.scenario(), plan.targetRps(), plan.durationSeconds());
    }

    public UUID executionId() {
        return plan.executionId();
    }

    public void cancel() {
        cancelRequested = true;
    }

    public boolean isFinished() {
        return finished.getCount() == 0;
    }

    public boolean awaitFinished(Duration timeout) throws InterruptedException {
        return finished.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public FinalReport report() {
        return report;
    }

    /** Executa até o fim. Nunca lança exceção: falhas resultam em status FAILED. */
    public FinalReport run() {
        long start = System.nanoTime();
        MetricsCollector metrics = new MetricsCollector(plan.totalRequests(), clock, start);
        Semaphore permits = new Semaphore(plan.concurrency());
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        Thread snapshots = Thread.ofVirtual().name("snapshots-" + plan.executionId())
                .start(() -> snapshotLoop(metrics, start));
        String status = "COMPLETED";
        String failureReason = null;
        try {
            dispatch(metrics, permits, workers, start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelRequested = true;
        } catch (RuntimeException e) {
            log.error("Falha na execução {}", plan.executionId(), e);
            status = "FAILED";
            failureReason = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            drain(workers);
            snapshots.interrupt();
            joinQuietly(snapshots);
            http.close();
        }
        if (cancelRequested && "COMPLETED".equals(status)) {
            status = "STOPPED";
        }
        long now = System.nanoTime();
        double progress = "COMPLETED".equals(status) ? 1.0 : progress(metrics, now - start);
        listener.onSnapshot(plan.executionId(), metrics.snapshot(now, start, currentRate(now - start), progress));
        report = metrics.finalReport(status, failureReason, now - start);
        try {
            listener.onFinished(plan.executionId(), report);
        } finally {
            finished.countDown();
        }
        log.info("Execução {} finalizada: {} ({} enviadas, {} respondidas)", plan.executionId(), status,
                report.totalRequests(), report.completed());
        return report;
    }

    private void dispatch(MetricsCollector metrics, Semaphore permits, ExecutorService workers, long start)
            throws InterruptedException {
        long durationNanos = TimeUnit.SECONDS.toNanos(plan.durationSeconds());
        double credit = 1.0; // primeira requisição sai imediatamente
        long last = start;
        int dispatched = 0;
        while (!cancelRequested && dispatched < plan.totalRequests()) {
            long now = System.nanoTime();
            long elapsed = now - start;
            if (elapsed >= durationNanos) {
                break;
            }
            double rate = profile.rateAt(elapsed / 1e9);
            // Créditos limitados a 1s de envio: evita rajadas de "recuperação" após saturação
            credit = Math.min(credit + rate * (now - last) / 1e9, Math.max(1.0, rate));
            last = now;
            if (credit < 1.0) {
                LockSupport.parkNanos(Math.min((long) ((1.0 - credit) / rate * 1e9), MAX_PARK_NANOS));
                continue;
            }
            // Concorrência saturada: aguarda uma vaga sem perder a verificação de parada
            if (!permits.tryAcquire(10, TimeUnit.MILLISECONDS)) {
                continue;
            }
            credit -= 1.0;
            dispatched++;
            metrics.onSent();
            workers.execute(() -> {
                try {
                    http.execute(metrics);
                } finally {
                    permits.release();
                }
            });
        }
    }

    /** Aguarda as requisições em andamento (limitadas pelo timeout) ou as interrompe no cancelamento. */
    private void drain(ExecutorService workers) {
        if (cancelRequested) {
            workers.shutdownNow();
        } else {
            workers.shutdown();
        }
        try {
            long waitMs = plan.timeoutMs() + DRAIN_GRACE.toMillis();
            if (!workers.awaitTermination(waitMs, TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
                workers.awaitTermination(DRAIN_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void snapshotLoop(MetricsCollector metrics, long start) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(snapshotInterval);
                long now = System.nanoTime();
                var snapshot = metrics.snapshot(now, start, currentRate(now - start), progress(metrics, now - start));
                if (!listener.onSnapshot(plan.executionId(), snapshot)) {
                    log.warn("Backend recusou snapshots da execução {}; cancelando", plan.executionId());
                    cancel();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("Falha ao publicar snapshot: {}", e.getMessage());
        }
    }

    private double currentRate(long elapsedNanos) {
        return profile.rateAt(elapsedNanos / 1e9);
    }

    private double progress(MetricsCollector metrics, long elapsedNanos) {
        double byRequests = (double) metrics.sent() / plan.totalRequests();
        double byTime = elapsedNanos / 1e9 / plan.durationSeconds();
        return Math.min(1.0, Math.max(byRequests, byTime));
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(Duration.ofSeconds(10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
