package br.com.joshua.stresslabsservice.loadtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;

class LoadTestConfigTest {

    static final LoadTestLimits LIMITS = new LoadTestLimits(1000, 50, 60, 500, 100, 10_000);
    static final AllowedTarget HEALTH = TargetCatalog.find("health").orElseThrow();
    static final AllowedTarget CREATE = TargetCatalog.find("product-create").orElseThrow();

    static LoadTestConfig create(AllowedTarget target, TargetHttpMethod method, int total, int concurrency, int rps,
                                 int duration, int timeout, Map<String, String> headers) {
        return LoadTestConfig.create(null, TestScenario.CONSTANT, target, method, total, concurrency, rps, duration,
                timeout, true, null, headers, LIMITS);
    }

    @Test
    void validConfigurationGetsDefaults() {
        LoadTestConfig config = create(HEALTH, null, 100, 10, 50, 10, 1000, null);
        assertThat(config.method()).isEqualTo(TargetHttpMethod.GET);
        assertThat(config.name()).isEqualTo("CONSTANT - health");
        assertThat(config.requestBody()).isNull();
        assertThat(config.headers()).isEmpty();
    }

    @Test
    void enforcesSafetyLimits() {
        assertThatThrownBy(() -> create(HEALTH, null, 1001, 10, 50, 10, 1000, null))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("requisições");
        assertThatThrownBy(() -> create(HEALTH, null, 100, 51, 50, 10, 1000, null))
                .hasMessageContaining("Concorrência");
        assertThatThrownBy(() -> create(HEALTH, null, 100, 10, 501, 10, 1000, null))
                .hasMessageContaining("RPS");
        assertThatThrownBy(() -> create(HEALTH, null, 100, 10, 50, 61, 1000, null))
                .hasMessageContaining("Duração");
        assertThatThrownBy(() -> create(HEALTH, null, 100, 10, 50, 10, 50, null))
                .hasMessageContaining("Timeout");
        assertThatThrownBy(() -> create(HEALTH, null, 0, 10, 50, 10, 1000, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void methodMustMatchTheAllowedTarget() {
        assertThatThrownBy(() -> create(HEALTH, TargetHttpMethod.POST, 10, 1, 1, 1, 1000, null))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("não permitido");
    }

    @Test
    void postUsesTheTargetDefaultBody() {
        LoadTestConfig config = create(CREATE, TargetHttpMethod.POST, 10, 1, 1, 1, 1000, null);
        assertThat(config.requestBody()).isEqualTo(CREATE.defaultBody());
    }

    @Test
    void onlyAllowedHeadersAreAccepted() {
        assertThat(create(HEALTH, null, 10, 1, 1, 1, 1000, Map.of("X-Client-Id", "abc")).headers())
                .containsEntry("X-Client-Id", "abc");
        assertThatThrownBy(() -> create(HEALTH, null, 10, 1, 1, 1, 1000, Map.of("Host", "evil.com")))
                .hasMessageContaining("Header não permitido");
        assertThatThrownBy(() -> create(HEALTH, null, 10, 1, 1, 1, 1000, Map.of("X-Client-Id", "a\r\nb")))
                .hasMessageContaining("Valor inválido");
    }
}
