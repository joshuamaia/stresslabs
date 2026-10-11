package br.com.joshua.stresslabsservice.metrics.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.joshua.stresslabsservice.metrics.application.port.out.MetricsRepositoryPort;
import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;
import br.com.joshua.stresslabsservice.metrics.domain.TestResult;

@Component
class MetricsPersistenceAdapter implements MetricsRepositoryPort {

    private final MetricSnapshotJpaRepository snapshots;
    private final TestResultJpaRepository results;

    MetricsPersistenceAdapter(MetricSnapshotJpaRepository snapshots, TestResultJpaRepository results) {
        this.snapshots = snapshots;
        this.results = results;
    }

    @Override
    @Transactional
    public void saveSnapshot(MetricSnapshot s) {
        var e = new MetricSnapshotJpaEntity();
        e.executionId = s.executionId();
        e.capturedAt = s.capturedAt();
        e.elapsedMs = s.elapsedMs();
        e.rps = s.rps();
        e.targetRps = s.targetRps();
        e.concurrency = s.concurrency();
        e.maxConcurrency = s.maxConcurrency();
        e.avgLatencyMs = s.avgLatencyMs();
        e.p95LatencyMs = s.p95LatencyMs();
        e.p99LatencyMs = s.p99LatencyMs();
        e.sent = s.sent();
        e.completed = s.completed();
        e.success = s.success();
        e.errors = s.errors();
        e.blocked = s.blocked();
        e.timeouts = s.timeouts();
        e.connectionFailures = s.connectionFailures();
        e.status2xx = s.status2xx();
        e.status4xx = s.status4xx();
        e.status5xx = s.status5xx();
        e.progress = s.progress();
        snapshots.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MetricSnapshot> findSnapshots(UUID executionId) {
        return snapshots.findByExecutionIdOrderByElapsedMsAsc(executionId).stream()
                .map(e -> new MetricSnapshot(e.executionId, e.capturedAt, e.elapsedMs, e.rps, e.targetRps,
                        e.concurrency, e.maxConcurrency, e.avgLatencyMs, e.p95LatencyMs, e.p99LatencyMs, e.sent,
                        e.completed, e.success, e.errors, e.blocked, e.timeouts, e.connectionFailures, e.status2xx,
                        e.status4xx, e.status5xx, e.progress))
                .toList();
    }

    @Override
    @Transactional
    public void saveResult(TestResult r) {
        var e = results.findByExecutionId(r.executionId()).orElseGet(TestResultJpaEntity::new);
        e.executionId = r.executionId();
        e.totalRequests = r.totalRequests();
        e.completedRequests = r.completedRequests();
        e.successCount = r.successCount();
        e.errorCount = r.errorCount();
        e.blockedCount = r.blockedCount();
        e.httpErrorCount = r.httpErrorCount();
        e.connectionFailures = r.connectionFailures();
        e.timeoutCount = r.timeoutCount();
        e.status2xx = r.status2xx();
        e.status3xx = r.status3xx();
        e.status4xx = r.status4xx();
        e.status5xx = r.status5xx();
        e.avgRps = r.avgRps();
        e.avgLatencyMs = r.avgLatencyMs();
        e.p95LatencyMs = r.p95LatencyMs();
        e.p99LatencyMs = r.p99LatencyMs();
        e.minLatencyMs = r.minLatencyMs();
        e.maxLatencyMs = r.maxLatencyMs();
        e.maxConcurrency = r.maxConcurrency();
        e.durationMs = r.durationMs();
        results.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TestResult> findResult(UUID executionId) {
        return results.findByExecutionId(executionId).map(MetricsPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, TestResult> findResults(Collection<UUID> executionIds) {
        if (executionIds.isEmpty()) {
            return Map.of();
        }
        return results.findByExecutionIdIn(executionIds).stream()
                .map(MetricsPersistenceAdapter::toDomain)
                .collect(Collectors.toMap(TestResult::executionId, Function.identity()));
    }

    private static TestResult toDomain(TestResultJpaEntity e) {
        return new TestResult(e.executionId, e.totalRequests, e.completedRequests, e.successCount, e.errorCount,
                e.blockedCount, e.httpErrorCount, e.connectionFailures, e.timeoutCount, e.status2xx, e.status3xx,
                e.status4xx, e.status5xx, e.avgRps, e.avgLatencyMs, e.p95LatencyMs, e.p99LatencyMs, e.minLatencyMs,
                e.maxLatencyMs, e.maxConcurrency, e.durationMs);
    }
}
