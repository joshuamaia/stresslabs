package br.com.joshua.stresslabsservice.ratelimit.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import br.com.joshua.stresslabsservice.ratelimit.domain.ClientIdentificationStrategy;

@Entity
@Table(name = "rate_limit_configuration")
public class RateLimitConfigurationJpaEntity {

    /** Configuração única (singleton) para o MVP. */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private long capacity;

    @Column(name = "refill_tokens", nullable = false)
    private long refillTokens;

    @Column(name = "refill_period_seconds", nullable = false)
    private long refillPeriodSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_strategy", nullable = false, length = 20)
    private ClientIdentificationStrategy clientStrategy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RateLimitConfigurationJpaEntity() {
    }

    public RateLimitConfigurationJpaEntity(boolean enabled, long capacity, long refillTokens, long refillPeriodSeconds,
                                           ClientIdentificationStrategy clientStrategy, Instant updatedAt) {
        this.id = SINGLETON_ID;
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodSeconds = refillPeriodSeconds;
        this.clientStrategy = clientStrategy;
        this.updatedAt = updatedAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getCapacity() {
        return capacity;
    }

    public long getRefillTokens() {
        return refillTokens;
    }

    public long getRefillPeriodSeconds() {
        return refillPeriodSeconds;
    }

    public ClientIdentificationStrategy getClientStrategy() {
        return clientStrategy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
