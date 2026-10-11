package br.com.joshua.stresslabsservice.ratelimit.application;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import br.com.joshua.stresslabsservice.ratelimit.application.port.out.RateLimitConfigRepositoryPort;
import br.com.joshua.stresslabsservice.ratelimit.application.port.out.TokenBucketPort;
import br.com.joshua.stresslabsservice.ratelimit.domain.ClientIdentificationStrategy;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitDecision;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitPolicy;

/**
 * Mantém a política vigente em memória (leitura sem lock no caminho quente) e
 * persiste alterações. Mudanças entram em vigor imediatamente, sem reinício.
 */
@Service
public class RateLimitService {

    private final RateLimitConfigRepositoryPort repository;
    private final TokenBucketPort buckets;
    private final Clock clock;
    private final AtomicReference<RateLimitPolicy> current = new AtomicReference<>();

    public RateLimitService(RateLimitConfigRepositoryPort repository, TokenBucketPort buckets, Clock clock) {
        this.repository = repository;
        this.buckets = buckets;
        this.clock = clock;
    }

    public RateLimitPolicy currentPolicy() {
        RateLimitPolicy policy = current.get();
        if (policy == null) {
            policy = repository.load().orElseGet(() -> repository.save(RateLimitPolicy.defaults(clock.instant())));
            current.compareAndSet(null, policy);
            policy = current.get();
        }
        return policy;
    }

    public RateLimitDecision check(String clientKey) {
        RateLimitPolicy policy = currentPolicy();
        if (!policy.enabled()) {
            return RateLimitDecision.unlimited();
        }
        return buckets.tryConsume(clientKey, policy);
    }

    public synchronized RateLimitPolicy update(UpdateRateLimitCommand command) {
        var policy = new RateLimitPolicy(command.enabled(), command.capacity(), command.refillTokens(),
                Duration.ofSeconds(command.refillPeriodSeconds()), command.clientStrategy(), clock.instant());
        return apply(policy);
    }

    public synchronized RateLimitPolicy setEnabled(boolean enabled) {
        RateLimitPolicy policy = currentPolicy();
        if (policy.enabled() == enabled) {
            return policy;
        }
        return apply(policy.withEnabled(enabled, clock.instant()));
    }

    /** Zera todos os buckets, garantindo que cada teste comece nas mesmas condições. */
    public void resetBuckets() {
        buckets.reset();
    }

    private RateLimitPolicy apply(RateLimitPolicy policy) {
        RateLimitPolicy saved = repository.save(policy);
        current.set(saved);
        buckets.reset();
        return saved;
    }

    public record UpdateRateLimitCommand(boolean enabled, long capacity, long refillTokens, long refillPeriodSeconds,
                                         ClientIdentificationStrategy clientStrategy) {
    }
}
