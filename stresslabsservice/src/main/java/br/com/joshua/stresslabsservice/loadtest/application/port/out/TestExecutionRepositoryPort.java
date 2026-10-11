package br.com.joshua.stresslabsservice.loadtest.application.port.out;

import java.util.Optional;
import java.util.UUID;

import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.shared.domain.PageResult;

public interface TestExecutionRepositoryPort {

    TestExecution save(TestExecution execution);

    Optional<TestExecution> findById(UUID id);

    Optional<TestExecution> findActive();

    PageResult<TestExecution> findPage(int page, int size);

    void deleteById(UUID id);

    long deleteAllFinished();
}
