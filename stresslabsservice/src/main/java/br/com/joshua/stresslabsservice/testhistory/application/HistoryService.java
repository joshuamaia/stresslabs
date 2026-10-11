package br.com.joshua.stresslabsservice.testhistory.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.joshua.stresslabsservice.loadtest.application.port.out.TestExecutionRepositoryPort;
import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.metrics.application.port.out.MetricsRepositoryPort;
import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;
import br.com.joshua.stresslabsservice.metrics.domain.TestResult;
import br.com.joshua.stresslabsservice.shared.domain.ConflictException;
import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;
import br.com.joshua.stresslabsservice.shared.domain.PageResult;
import br.com.joshua.stresslabsservice.shared.domain.ResourceNotFoundException;
import br.com.joshua.stresslabsservice.testhistory.domain.ComparisonMetric;
import br.com.joshua.stresslabsservice.testhistory.domain.ExecutionComparison;

@Service
public class HistoryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final TestExecutionRepositoryPort executions;
    private final MetricsRepositoryPort metrics;

    public HistoryService(TestExecutionRepositoryPort executions, MetricsRepositoryPort metrics) {
        this.executions = executions;
        this.metrics = metrics;
    }

    public PageResult<ExecutionSummary> list(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainValidationException("page >= 0 e size entre 1 e " + MAX_PAGE_SIZE);
        }
        PageResult<TestExecution> result = executions.findPage(page, size);
        Map<UUID, TestResult> results = metrics.findResults(result.content().stream().map(TestExecution::id).toList());
        return result.map(execution -> new ExecutionSummary(execution, results.get(execution.id())));
    }

    public ExecutionDetail detail(UUID id) {
        TestExecution execution = find(id);
        return new ExecutionDetail(execution, metrics.findResult(id).orElse(null), metrics.findSnapshots(id));
    }

    public Comparison compare(UUID idA, UUID idB) {
        if (idA.equals(idB)) {
            throw new DomainValidationException("Selecione duas execuções diferentes");
        }
        ExecutionDetail a = detail(idA);
        ExecutionDetail b = detail(idB);
        return new Comparison(a, b, ExecutionComparison.compare(a.result(), b.result()));
    }

    public void delete(UUID id) {
        if (find(id).status().isActive()) {
            throw new ConflictException("Não é possível excluir uma execução em andamento");
        }
        executions.deleteById(id);
    }

    public long clear() {
        return executions.deleteAllFinished();
    }

    private TestExecution find(UUID id) {
        return executions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execução " + id + " não encontrada"));
    }

    public record ExecutionSummary(TestExecution execution, TestResult result) {
    }

    public record ExecutionDetail(TestExecution execution, TestResult result, List<MetricSnapshot> snapshots) {
    }

    public record Comparison(ExecutionDetail a, ExecutionDetail b, List<ComparisonMetric> metrics) {
    }
}
