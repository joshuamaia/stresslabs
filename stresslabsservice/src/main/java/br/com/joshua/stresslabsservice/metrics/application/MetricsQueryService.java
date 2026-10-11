package br.com.joshua.stresslabsservice.metrics.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.joshua.stresslabsservice.metrics.application.port.out.InfrastructureMetricsPort;
import br.com.joshua.stresslabsservice.metrics.application.port.out.MetricsRepositoryPort;
import br.com.joshua.stresslabsservice.metrics.domain.InfrastructureMetrics;
import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;
import br.com.joshua.stresslabsservice.metrics.domain.TestResult;

@Service
public class MetricsQueryService {

    private final MetricsRepositoryPort repository;
    private final InfrastructureMetricsPort infrastructure;

    public MetricsQueryService(MetricsRepositoryPort repository, InfrastructureMetricsPort infrastructure) {
        this.repository = repository;
        this.infrastructure = infrastructure;
    }

    public List<MetricSnapshot> snapshots(UUID executionId) {
        return repository.findSnapshots(executionId);
    }

    public Optional<TestResult> result(UUID executionId) {
        return repository.findResult(executionId);
    }

    public InfrastructureMetrics infrastructure() {
        return infrastructure.read();
    }
}
