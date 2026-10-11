package br.com.joshua.stresslabsservice.loadtest.adapter.out.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import br.com.joshua.stresslabsservice.loadtest.domain.TestStatus;

interface TestExecutionJpaRepository extends JpaRepository<TestExecutionJpaEntity, UUID> {

    Optional<TestExecutionJpaEntity> findFirstByStatusInOrderByCreatedAtDesc(Collection<TestStatus> statuses);

    /** Snapshots e resultados são removidos via ON DELETE CASCADE no banco. */
    @Modifying
    @Query(value = "DELETE FROM test_execution WHERE status IN ('COMPLETED', 'STOPPED', 'FAILED')",
            nativeQuery = true)
    int deleteAllFinished();
}
