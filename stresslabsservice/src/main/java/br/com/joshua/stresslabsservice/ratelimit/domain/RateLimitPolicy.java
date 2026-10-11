package br.com.joshua.stresslabsservice.ratelimit.domain;

import java.time.Duration;
import java.time.Instant;

import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;

/**
 * Configuração do token bucket.
 * capacity: máximo de requisições em rajada; refillTokens a cada refillPeriod: taxa de reposição.
 */
public record RateLimitPolicy(
        boolean enabled,
        long capacity,
        long refillTokens,
        Duration refillPeriod,
        ClientIdentificationStrategy clientStrategy,
        Instant updatedAt) {

    public static final long MAX_CAPACITY = 1_000_000;
    public static final Duration MAX_REFILL_PERIOD = Duration.ofHours(1);

    public RateLimitPolicy {
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new DomainValidationException("Capacidade deve estar entre 1 e " + MAX_CAPACITY);
        }
        if (refillTokens < 1 || refillTokens > MAX_CAPACITY) {
            throw new DomainValidationException("Tokens de reposição devem estar entre 1 e " + MAX_CAPACITY);
        }
        if (refillPeriod == null || refillPeriod.isNegative() || refillPeriod.isZero()
                || refillPeriod.compareTo(MAX_REFILL_PERIOD) > 0) {
            throw new DomainValidationException("Intervalo de reposição deve estar entre 1s e 1h");
        }
        if (clientStrategy == null) {
            throw new DomainValidationException("Política de identificação do cliente é obrigatória");
        }
    }

    public static RateLimitPolicy defaults(Instant now) {
        return new RateLimitPolicy(true, 100, 100, Duration.ofMinutes(1), ClientIdentificationStrategy.IP, now);
    }

    public RateLimitPolicy withEnabled(boolean value, Instant now) {
        return new RateLimitPolicy(value, capacity, refillTokens, refillPeriod, clientStrategy, now);
    }

    public String summary() {
        if (!enabled) {
            return "Desabilitado";
        }
        return "%d req (capacidade) / reposição %d a cada %ds por %s"
                .formatted(capacity, refillTokens, refillPeriod.toSeconds(), clientStrategy);
    }
}
