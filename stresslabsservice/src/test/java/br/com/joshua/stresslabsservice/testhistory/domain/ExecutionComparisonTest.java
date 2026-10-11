package br.com.joshua.stresslabsservice.testhistory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.joshua.stresslabsservice.metrics.domain.TestResult;
import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;

class ExecutionComparisonTest {

    private static TestResult result(double rps, double p95, long success, long blocked) {
        return new TestResult(UUID.randomUUID(), 200, 200, success, 0, blocked, 0, 0, 0, success, 0, blocked, 0, rps,
                10, p95, p95 + 5, 1, 50, 10, 10_000);
    }

    private static ComparisonMetric metric(List<ComparisonMetric> metrics, String key) {
        return metrics.stream().filter(m -> m.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void computesObservedDifferencesWithoutAssumingAWinner() {
        TestResult withoutLimit = result(100, 40, 200, 0);
        TestResult withLimit = result(80, 20, 100, 100);

        List<ComparisonMetric> metrics = ExecutionComparison.compare(withoutLimit, withLimit);

        ComparisonMetric throughput = metric(metrics, "throughput");
        assertThat(throughput.difference()).isEqualTo(-20);
        assertThat(throughput.percentChange()).isCloseTo(-20.0, within(1e-9));

        ComparisonMetric p95 = metric(metrics, "p95");
        assertThat(p95.difference()).isEqualTo(-20);
        assertThat(p95.preference()).isEqualTo(ComparisonMetric.Preference.LOWER_IS_BETTER);

        ComparisonMetric blocked = metric(metrics, "blocked");
        assertThat(blocked.valueB()).isEqualTo(100);
        assertThat(blocked.percentChange()).isNull(); // base zero
        assertThat(blocked.preference()).isEqualTo(ComparisonMetric.Preference.NEUTRAL);

        assertThat(metric(metrics, "successRate").valueA()).isEqualTo(100.0);
        assertThat(metric(metrics, "successRate").valueB()).isEqualTo(50.0);
    }

    @Test
    void requiresBothResults() {
        assertThatThrownBy(() -> ExecutionComparison.compare(null, result(1, 1, 1, 0)))
                .isInstanceOf(DomainValidationException.class);
    }
}
