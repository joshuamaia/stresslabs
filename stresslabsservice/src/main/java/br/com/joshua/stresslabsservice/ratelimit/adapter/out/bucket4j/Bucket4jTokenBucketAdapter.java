package br.com.joshua.stresslabsservice.ratelimit.adapter.out.bucket4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import br.com.joshua.stresslabsservice.ratelimit.application.port.out.TokenBucketPort;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitDecision;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitPolicy;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * Buckets em memória (Bucket4j é lock-free e thread-safe).
 * Em ambiente distribuído seria necessário estado compartilhado (ex.: bucket4j com JDBC/Redis).
 */
@Component
class Bucket4jTokenBucketAdapter implements TokenBucketPort {

    static final int MAX_TRACKED_CLIENTS = 50_000;

    private final AtomicReference<BucketTable> table = new AtomicReference<>(new BucketTable(null));

    @Override
    public RateLimitDecision tryConsume(String clientKey, RateLimitPolicy policy) {
        BucketTable current = table.get();
        if (current.policy() != policy) {
            // Nova política: descarta os buckets antigos de forma atômica
            BucketTable replacement = new BucketTable(policy);
            current = table.compareAndSet(current, replacement) ? replacement : table.get();
        }
        if (current.buckets().size() >= MAX_TRACKED_CLIENTS && !current.buckets().containsKey(clientKey)) {
            current.buckets().clear();
        }
        RateLimitPolicy effective = current.policy() != null ? current.policy() : policy;
        Bucket bucket = current.buckets().computeIfAbsent(clientKey, key -> newBucket(effective));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        long retryAfter = probe.isConsumed() ? 0
                : Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999L));
        return new RateLimitDecision(probe.isConsumed(), effective.capacity(), probe.getRemainingTokens(), retryAfter);
    }

    @Override
    public void reset() {
        table.set(new BucketTable(null));
    }

    private static Bucket newBucket(RateLimitPolicy policy) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(policy.capacity())
                .refillIntervally(policy.refillTokens(), policy.refillPeriod())
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private record BucketTable(RateLimitPolicy policy, ConcurrentMap<String, Bucket> buckets) {
        BucketTable(RateLimitPolicy policy) {
            this(policy, new ConcurrentHashMap<>());
        }
    }
}
