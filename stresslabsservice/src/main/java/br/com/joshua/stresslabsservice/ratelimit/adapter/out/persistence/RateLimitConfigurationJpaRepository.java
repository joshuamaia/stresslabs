package br.com.joshua.stresslabsservice.ratelimit.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface RateLimitConfigurationJpaRepository extends JpaRepository<RateLimitConfigurationJpaEntity, Long> {
}
