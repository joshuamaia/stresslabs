package br.com.joshua.loadgen.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class LatencyStatsTest {

    @Test
    void nearestRankPercentilesOnOneToHundred() {
        long[] values = LongStream.rangeClosed(1, 100).toArray();
        assertThat(LatencyStats.percentile(values, 50)).isEqualTo(50);
        assertThat(LatencyStats.percentile(values, 95)).isEqualTo(95);
        assertThat(LatencyStats.percentile(values, 99)).isEqualTo(99);
        assertThat(LatencyStats.percentile(values, 100)).isEqualTo(100);
    }

    @Test
    void percentileOnSmallSamplesRoundsRankUp() {
        long[] values = {10, 20, 30};
        assertThat(LatencyStats.percentile(values, 95)).isEqualTo(30);
        assertThat(LatencyStats.percentile(values, 1)).isEqualTo(10);
        assertThat(LatencyStats.percentile(new long[] {7}, 99)).isEqualTo(7);
    }

    @Test
    void statsSortInputAndConvertNanosToMillis() {
        long[] nanos = {3_000_000, 1_000_000, 2_000_000, 4_000_000};
        LatencyStats stats = LatencyStats.fromNanos(nanos);
        assertThat(stats.count()).isEqualTo(4);
        assertThat(stats.avgMs()).isEqualTo(2.5);
        assertThat(stats.minMs()).isEqualTo(1.0);
        assertThat(stats.maxMs()).isEqualTo(4.0);
        assertThat(stats.p95Ms()).isEqualTo(4.0);
        assertThat(stats.p99Ms()).isEqualTo(4.0);
    }

    @Test
    void emptySamplesProduceZeroStats() {
        assertThat(LatencyStats.fromNanos(new long[0])).isEqualTo(LatencyStats.EMPTY);
    }

    @Test
    void rejectsInvalidPercentile() {
        assertThatThrownBy(() -> LatencyStats.percentile(new long[] {1}, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LatencyStats.percentile(new long[0], 50))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
