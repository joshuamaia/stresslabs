package br.com.joshua.stresslabsservice.ratelimit.application.port.out;

import java.util.Optional;

import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitPolicy;

public interface RateLimitConfigRepositoryPort {

    Optional<RateLimitPolicy> load();

    RateLimitPolicy save(RateLimitPolicy policy);
}
