package br.com.joshua.stresslabsservice.loadtest.adapter.out.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.joshua.stresslabsservice.loadtest.application.port.out.TestExecutionRepositoryPort;
import br.com.joshua.stresslabsservice.loadtest.domain.AllowedTarget;
import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestConfig;
import br.com.joshua.stresslabsservice.loadtest.domain.TargetCatalog;
import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.loadtest.domain.TestStatus;
import br.com.joshua.stresslabsservice.shared.domain.PageResult;

@Component
class TestExecutionPersistenceAdapter implements TestExecutionRepositoryPort {

    private static final List<TestStatus> ACTIVE = List.of(TestStatus.PENDING, TestStatus.RUNNING);

    private final TestExecutionJpaRepository repository;

    TestExecutionPersistenceAdapter(TestExecutionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public TestExecution save(TestExecution execution) {
        repository.save(toEntity(execution));
        return execution;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TestExecution> findById(UUID id) {
        return repository.findById(id).map(TestExecutionPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TestExecution> findActive() {
        return repository.findFirstByStatusInOrderByCreatedAtDesc(ACTIVE)
                .map(TestExecutionPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TestExecution> findPage(int page, int size) {
        Page<TestExecutionJpaEntity> result = repository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResult<>(result.getContent().stream().map(TestExecutionPersistenceAdapter::toDomain).toList(),
                page, size, result.getTotalElements());
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public long deleteAllFinished() {
        return repository.deleteAllFinished();
    }

    private static TestExecutionJpaEntity toEntity(TestExecution execution) {
        LoadTestConfig config = execution.config();
        var e = new TestExecutionJpaEntity();
        e.id = execution.id();
        e.name = config.name();
        e.scenario = config.scenario();
        e.targetKey = config.target().key();
        e.endpoint = config.target().path();
        e.httpMethod = config.method();
        e.totalRequests = config.totalRequests();
        e.concurrency = config.concurrency();
        e.targetRps = config.targetRps();
        e.durationSeconds = config.durationSeconds();
        e.timeoutMs = config.timeoutMs();
        e.rateLimitEnabled = config.rateLimitEnabled();
        e.rateLimitSummary = execution.rateLimitSummary();
        e.requestBody = config.requestBody();
        e.status = execution.status();
        e.failureReason = execution.failureReason();
        e.createdAt = execution.createdAt();
        e.startedAt = execution.startedAt();
        e.finishedAt = execution.finishedAt();
        return e;
    }

    private static TestExecution toDomain(TestExecutionJpaEntity e) {
        // O catálogo é a fonte de verdade; se um destino antigo deixar de existir, preserva o caminho gravado
        AllowedTarget target = TargetCatalog.find(e.targetKey)
                .orElseGet(() -> new AllowedTarget(e.targetKey, e.targetKey, e.httpMethod, e.endpoint, "", null, 0));
        var config = new LoadTestConfig(e.name, e.scenario, target, e.httpMethod, e.totalRequests, e.concurrency,
                e.targetRps, e.durationSeconds, e.timeoutMs, e.rateLimitEnabled, e.requestBody, Map.of());
        return new TestExecution(e.id, config, e.rateLimitSummary, e.createdAt, e.status, e.failureReason,
                e.startedAt, e.finishedAt);
    }
}
