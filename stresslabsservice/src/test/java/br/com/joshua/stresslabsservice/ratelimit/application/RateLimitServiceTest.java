package br.com.joshua.stresslabsservice.ratelimit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.joshua.stresslabsservice.ratelimit.application.RateLimitService.UpdateRateLimitCommand;
import br.com.joshua.stresslabsservice.ratelimit.application.port.out.RateLimitConfigRepositoryPort;
import br.com.joshua.stresslabsservice.ratelimit.application.port.out.TokenBucketPort;
import br.com.joshua.stresslabsservice.ratelimit.domain.ClientIdentificationStrategy;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitPolicy;
import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RateLimitConfigRepositoryPort repository;
    @Mock
    private TokenBucketPort buckets;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService(repository, buckets, CLOCK);
    }

    @Test
    void disabledPolicyDoesNotConsumeTokens() {
        when(repository.load()).thenReturn(Optional.of(RateLimitPolicy.defaults(CLOCK.instant())
                .withEnabled(false, CLOCK.instant())));

        assertThat(service.check("ip:1").allowed()).isTrue();
        assertThat(service.check("ip:1").limited()).isFalse();
        verify(buckets, never()).tryConsume(anyString(), any());
    }

    @Test
    void updatePersistsAppliesImmediatelyAndResetsBuckets() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RateLimitPolicy updated = service.update(new UpdateRateLimitCommand(true, 10, 5, 30,
                ClientIdentificationStrategy.GLOBAL));

        assertThat(updated.capacity()).isEqualTo(10);
        assertThat(service.currentPolicy()).isSameAs(updated);
        verify(buckets).reset();
    }

    @Test
    void createsDefaultPolicyWhenNoneIsStored() {
        when(repository.load()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.currentPolicy().capacity()).isEqualTo(100);
    }

    @Test
    void invalidUpdateIsRejected() {
        assertThatThrownBy(() -> service.update(new UpdateRateLimitCommand(true, 0, 5, 30,
                ClientIdentificationStrategy.IP))).isInstanceOf(DomainValidationException.class);
        verify(repository, never()).save(any());
    }
}
