package br.com.joshua.stresslabsservice.ratelimit.adapter.out.bucket4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import br.com.joshua.stresslabsservice.ratelimit.domain.ClientIdentificationStrategy;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitDecision;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitPolicy;

class Bucket4jTokenBucketAdapterTest {

    private final Bucket4jTokenBucketAdapter adapter = new Bucket4jTokenBucketAdapter();

    private static RateLimitPolicy policy(long capacity) {
        return new RateLimitPolicy(true, capacity, capacity, Duration.ofMinutes(1), ClientIdentificationStrategy.IP,
                Instant.now());
    }

    @Test
    void blocksAfterCapacityIsExhaustedWithRetryAfter() {
        RateLimitPolicy policy = policy(3);
        for (int i = 0; i < 3; i++) {
            RateLimitDecision decision = adapter.tryConsume("client", policy);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isEqualTo(2 - i);
        }
        RateLimitDecision blocked = adapter.tryConsume("client", policy);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.limit()).isEqualTo(3);
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void bucketsAreIsolatedPerClient() {
        RateLimitPolicy policy = policy(1);
        assertThat(adapter.tryConsume("a", policy).allowed()).isTrue();
        assertThat(adapter.tryConsume("a", policy).allowed()).isFalse();
        assertThat(adapter.tryConsume("b", policy).allowed()).isTrue();
    }

    @Test
    void resetAndNewPolicyStartFreshBuckets() {
        RateLimitPolicy policy = policy(1);
        adapter.tryConsume("a", policy);
        assertThat(adapter.tryConsume("a", policy).allowed()).isFalse();

        adapter.reset();
        assertThat(adapter.tryConsume("a", policy).allowed()).isTrue();

        assertThat(adapter.tryConsume("a", policy(5)).allowed()).isTrue();
    }

    @Test
    void neverAllowsMoreThanCapacityUnderConcurrency() throws Exception {
        RateLimitPolicy policy = policy(50);
        int threads = 200;
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> task = () -> {
                    start.await();
                    return adapter.tryConsume("shared", policy).allowed();
                };
                results.add(executor.submit(task));
            }
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(50);
        }
    }
}
