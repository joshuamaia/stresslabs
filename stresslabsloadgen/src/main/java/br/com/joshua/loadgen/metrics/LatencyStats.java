package br.com.joshua.loadgen.metrics;

import java.util.Arrays;

/** Estatísticas de latência (ms). Percentis pelo método nearest-rank, sem interpolação. */
public record LatencyStats(long count, double avgMs, double p95Ms, double p99Ms, double minMs, double maxMs) {

    public static final LatencyStats EMPTY = new LatencyStats(0, 0, 0, 0, 0, 0);

    /** Calcula a partir de latências em nanossegundos. O array é ordenado in-place. */
    public static LatencyStats fromNanos(long[] latenciesNanos) {
        if (latenciesNanos.length == 0) {
            return EMPTY;
        }
        Arrays.sort(latenciesNanos);
        double sum = 0;
        for (long value : latenciesNanos) {
            sum += value;
        }
        return new LatencyStats(latenciesNanos.length,
                toMs(sum / latenciesNanos.length),
                toMs(percentile(latenciesNanos, 95)),
                toMs(percentile(latenciesNanos, 99)),
                toMs(latenciesNanos[0]),
                toMs(latenciesNanos[latenciesNanos.length - 1]));
    }

    /** Nearest-rank: menor valor tal que ao menos p% das amostras são menores ou iguais a ele. */
    public static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) {
            throw new IllegalArgumentException("Sem amostras");
        }
        if (p <= 0 || p > 100) {
            throw new IllegalArgumentException("Percentil deve estar em (0, 100]");
        }
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.max(rank, 1) - 1];
    }

    private static double toMs(double nanos) {
        return Math.round(nanos / 1_000.0) / 1_000.0;
    }
}
