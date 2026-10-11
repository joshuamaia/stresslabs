package br.com.joshua.stresslabsservice.testhistory.domain;

import static br.com.joshua.stresslabsservice.testhistory.domain.ComparisonMetric.Preference.HIGHER_IS_BETTER;
import static br.com.joshua.stresslabsservice.testhistory.domain.ComparisonMetric.Preference.LOWER_IS_BETTER;
import static br.com.joshua.stresslabsservice.testhistory.domain.ComparisonMetric.Preference.NEUTRAL;

import java.util.List;

import br.com.joshua.stresslabsservice.metrics.domain.TestResult;
import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;

public final class ExecutionComparison {

    private ExecutionComparison() {
    }

    public static List<ComparisonMetric> compare(TestResult a, TestResult b) {
        if (a == null || b == null) {
            throw new DomainValidationException("Ambas as execuções precisam ter resultado consolidado");
        }
        return List.of(
                ComparisonMetric.of("totalRequests", "Requisições enviadas", "", a.totalRequests(),
                        b.totalRequests(), NEUTRAL),
                ComparisonMetric.of("throughput", "Throughput médio", "req/s", a.avgRps(), b.avgRps(),
                        HIGHER_IS_BETTER),
                ComparisonMetric.of("avgLatency", "Latência média", "ms", a.avgLatencyMs(), b.avgLatencyMs(),
                        LOWER_IS_BETTER),
                ComparisonMetric.of("p95", "Latência p95", "ms", a.p95LatencyMs(), b.p95LatencyMs(),
                        LOWER_IS_BETTER),
                ComparisonMetric.of("p99", "Latência p99", "ms", a.p99LatencyMs(), b.p99LatencyMs(),
                        LOWER_IS_BETTER),
                ComparisonMetric.of("errors", "Erros (HTTP, conexão, timeout)", "", a.errorCount(),
                        b.errorCount(), LOWER_IS_BETTER),
                ComparisonMetric.of("blocked", "Bloqueios HTTP 429", "", a.blockedCount(), b.blockedCount(),
                        NEUTRAL),
                ComparisonMetric.of("successRate", "Taxa de sucesso", "%", a.successRate(), b.successRate(),
                        HIGHER_IS_BETTER),
                ComparisonMetric.of("duration", "Tempo total", "ms", a.durationMs(), b.durationMs(), NEUTRAL),
                ComparisonMetric.of("maxConcurrency", "Concorrência máxima", "", a.maxConcurrency(),
                        b.maxConcurrency(), NEUTRAL));
    }
}
