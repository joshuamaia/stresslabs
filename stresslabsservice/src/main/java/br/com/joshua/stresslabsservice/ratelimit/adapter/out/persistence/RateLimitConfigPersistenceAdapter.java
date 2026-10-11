package br.com.joshua.stresslabsservice.ratelimit.adapter.out.persistence;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.joshua.stresslabsservice.ratelimit.application.port.out.RateLimitConfigRepositoryPort;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitPolicy;

@Component
class RateLimitConfigPersistenceAdapter implements RateLimitConfigRepositoryPort {

    private final RateLimitConfigurationJpaRepository repository;

    RateLimitConfigPersistenceAdapter(RateLimitConfigurationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RateLimitPolicy> load() {
        return repository.findById(RateLimitConfigurationJpaEntity.SINGLETON_ID).map(entity -> new RateLimitPolicy(
                entity.isEnabled(), entity.getCapacity(), entity.getRefillTokens(),
                Duration.ofSeconds(entity.getRefillPeriodSeconds()), entity.getClientStrategy(),
                entity.getUpdatedAt()));
    }

    @Override
    @Transactional
    public RateLimitPolicy save(RateLimitPolicy policy) {
        repository.save(new RateLimitConfigurationJpaEntity(policy.enabled(), policy.capacity(), policy.refillTokens(),
                policy.refillPeriod().toSeconds(), policy.clientStrategy(), policy.updatedAt()));
        return policy;
    }
}
