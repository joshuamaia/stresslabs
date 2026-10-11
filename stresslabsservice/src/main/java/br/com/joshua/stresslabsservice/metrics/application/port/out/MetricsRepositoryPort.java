package br.com.joshua.stresslabsservice.metrics.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;
import br.com.joshua.stresslabsservice.metrics.domain.TestResult;

public interface MetricsRepositoryPort {

    void saveSnapshot(MetricSnapshot snapshot);

    List<MetricSnapshot> findSnapshots(UUID executionId);

    void saveResult(TestResult result);

    Optional<TestResult> findResult(UUID executionId);

    Map<UUID, TestResult> findResults(Collection<UUID> executionIds);
}
