package br.com.joshua.stresslabsservice.metrics.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface MetricSnapshotJpaRepository extends JpaRepository<MetricSnapshotJpaEntity, Long> {

    List<MetricSnapshotJpaEntity> findByExecutionIdOrderByElapsedMsAsc(UUID executionId);
}
