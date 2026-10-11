package br.com.joshua.loadgen.metrics;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Agregação em memória, thread-safe, das métricas de uma execução.
 * Durações usam exclusivamente System.nanoTime() (relógio monotônico); Clock é usado só para timestamps.
 */
public class MetricsCollector {

    private final LongAdder sent = new LongAdder();
    private final LongAdder completed = new LongAdder();
    private final LongAdder success = new LongAdder();
    private final LongAdder blocked = new LongAdder();
    private final LongAdder httpErrors = new LongAdder();
    private final LongAdder connectionFailures = new LongAdder();
    private final LongAdder timeouts = new LongAdder();
    private final LongAdder cancelled = new LongAdder();
    private final LongAdder status2xx = new LongAdder();
    private final LongAdder status3xx = new LongAdder();
    private final LongAdder status4xx = new LongAdder();
    private final LongAdder status5xx = new LongAdder();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger windowMaxInFlight = new AtomicInteger();
    private final AtomicInteger globalMaxInFlight = new AtomicInteger();
    private final LatencyRecorder latencies;
    private final Clock clock;

    // Estado da janela, acessado apenas pela thread de snapshots
    private int windowLatencyStart;
    private long windowStartNanos;
    private long windowCompletedStart;

    public MetricsCollector(int expectedRequests, Clock clock, long startNanos) {
        this.latencies = new LatencyRecorder(expectedRequests);
        this.clock = clock;
        this.windowStartNanos = startNanos;
    }

    public void onSent() {
        sent.increment();
    }

    public void onRequestStart() {
        int current = inFlight.incrementAndGet();
        windowMaxInFlight.accumulateAndGet(current, Math::max);
        globalMaxInFlight.accumulateAndGet(current, Math::max);
    }

    public void onRequestEnd() {
        inFlight.decrementAndGet();
    }

    public void onResponse(int status, long latencyNanos) {
        completed.increment();
        latencies.record(latencyNanos);
        switch (status / 100) {
            case 2 -> status2xx.increment();
            case 3 -> status3xx.increment();
            case 4 -> status4xx.increment();
            case 5 -> status5xx.increment();
            default -> { }
        }
        switch (Outcome.fromStatus(status)) {
            case SUCCESS -> success.increment();
            case BLOCKED -> blocked.increment();
            default -> httpErrors.increment();
        }
    }

    public void onFailure(Outcome outcome) {
        switch (outcome) {
            case CONNECTION_FAILURE -> connectionFailures.increment();
            case TIMEOUT -> timeouts.increment();
            case CANCELLED -> cancelled.increment();
            default -> throw new IllegalArgumentException("Use onResponse para respostas HTTP: " + outcome);
        }
    }

    public int inFlight() {
        return inFlight.get();
    }

    public long sent() {
        return sent.sum();
    }

    public long cancelled() {
        return cancelled.sum();
    }

    /** Fecha a janela atual e retorna o snapshot. Chamado por uma única thread. */
    public synchronized Snapshot snapshot(long nowNanos, long startNanos, double targetRps, double progress) {
        int latencyEnd = latencies.size();
        LatencyStats window = LatencyStats.fromNanos(latencies.copyRange(windowLatencyStart, latencyEnd));
        long completedNow = completed.sum();
        double windowSeconds = Math.max((nowNanos - windowStartNanos) / 1e9, 1e-3);
        double rps = (completedNow - windowCompletedStart) / windowSeconds;

        int maxInFlight = windowMaxInFlight.getAndSet(inFlight.get());
        windowLatencyStart = latencyEnd;
        windowStartNanos = nowNanos;
        windowCompletedStart = completedNow;

        return new Snapshot(clock.instant(), (nowNanos - startNanos) / 1_000_000, round(rps), round(targetRps),
                inFlight.get(), maxInFlight, window.avgMs(), window.p95Ms(), window.p99Ms(), sent.sum(),
                completedNow, success.sum(), blocked.sum(), httpErrors.sum(), connectionFailures.sum(),
                timeouts.sum(), status2xx.sum(), status4xx.sum(), status5xx.sum(), Math.clamp(progress, 0.0, 1.0));
    }

    public FinalReport finalReport(String status, String failureReason, long durationNanos) {
        LatencyStats all = LatencyStats.fromNanos(latencies.copyAll());
        long completedTotal = completed.sum();
        double seconds = Math.max(durationNanos / 1e9, 1e-3);
        return new FinalReport(status, failureReason, sent.sum(), completedTotal, success.sum(), blocked.sum(),
                httpErrors.sum(), connectionFailures.sum(), timeouts.sum(), status2xx.sum(), status3xx.sum(),
                status4xx.sum(), status5xx.sum(), round(completedTotal / seconds), all.avgMs(), all.p95Ms(),
                all.p99Ms(), all.minMs(), all.maxMs(), globalMaxInFlight.get(), durationNanos / 1_000_000);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
