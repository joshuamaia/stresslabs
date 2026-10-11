package br.com.joshua.stresslabsservice.metrics.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TestResultJpaRepository extends JpaRepository<TestResultJpaEntity, Long> {

    Optional<TestResultJpaEntity> findByExecutionId(UUID executionId);

    List<TestResultJpaEntity> findByExecutionIdIn(Collection<UUID> executionIds);
}
