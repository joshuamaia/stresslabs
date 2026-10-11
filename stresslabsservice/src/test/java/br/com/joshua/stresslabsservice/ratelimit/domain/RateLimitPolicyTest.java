package br.com.joshua.stresslabsservice.ratelimit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;

class RateLimitPolicyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void defaultsAreOneHundredRequestsPerMinutePerIp() {
        RateLimitPolicy policy = RateLimitPolicy.defaults(NOW);
        assertThat(policy.enabled()).isTrue();
        assertThat(policy.capacity()).isEqualTo(100);
        assertThat(policy.refillTokens()).isEqualTo(100);
        assertThat(policy.refillPeriod()).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.clientStrategy()).isEqualTo(ClientIdentificationStrategy.IP);
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> new RateLimitPolicy(true, 0, 1, Duration.ofSeconds(1),
                ClientIdentificationStrategy.IP, NOW)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new RateLimitPolicy(true, 1, 0, Duration.ofSeconds(1),
                ClientIdentificationStrategy.IP, NOW)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new RateLimitPolicy(true, 1, 1, Duration.ZERO,
                ClientIdentificationStrategy.IP, NOW)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new RateLimitPolicy(true, 1, 1, Duration.ofHours(2),
                ClientIdentificationStrategy.IP, NOW)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new RateLimitPolicy(true, 1, 1, Duration.ofSeconds(1), null, NOW))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void withEnabledKeepsTheOtherSettings() {
        RateLimitPolicy disabled = RateLimitPolicy.defaults(NOW).withEnabled(false, NOW.plusSeconds(1));
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.capacity()).isEqualTo(100);
        assertThat(disabled.summary()).isEqualTo("Desabilitado");
    }
}
