package br.com.joshua.stresslabsservice.ratelimit.application.port.out;

import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitDecision;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitPolicy;

/** Armazenamento dos buckets. MVP em memória (instância única). */
public interface TokenBucketPort {

    RateLimitDecision tryConsume(String clientKey, RateLimitPolicy policy);

    void reset();
}
