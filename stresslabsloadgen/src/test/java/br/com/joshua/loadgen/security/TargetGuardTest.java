package br.com.joshua.loadgen.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.joshua.loadgen.security.TargetGuard.TargetNotAllowedException;

class TargetGuardTest {

    private final TargetGuard guard = new TargetGuard(URI.create("http://api-backend:8080"), List.of("/api/target/"));

    @Test
    void acceptsInternalPathsAndResolvesIdPlaceholder() {
        String path = guard.validatePath("/api/target/products/{id}");
        assertThat(guard.resolve(path, 42)).isEqualTo(URI.create("http://api-backend:8080/api/target/products/42"));
        assertThat(guard.validatePath("/api/target/products?page=0&size=20")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://evil.com/api/target/health",
            "//evil.com/api/target/health",
            "/api/admin/rate-limit",
            "/api/target/../admin/rate-limit",
            "/api/target/health@evil.com",
            "/api/target/health#x",
            "/api/target/ health",
            ""
    })
    void rejectsArbitraryOrUnauthorizedTargets(String path) {
        assertThatThrownBy(() -> guard.validatePath(path)).isInstanceOf(TargetNotAllowedException.class);
    }

    @Test
    void onlyAllowsWhitelistedHeaders() {
        guard.validateHeaders(Map.of("X-Client-Id", "client-a", "Accept", "application/json"));
        assertThatThrownBy(() -> guard.validateHeaders(Map.of("Host", "evil.com")))
                .isInstanceOf(TargetNotAllowedException.class);
        assertThatThrownBy(() -> guard.validateHeaders(Map.of("X-Client-Id", "bad\nvalue")))
                .isInstanceOf(TargetNotAllowedException.class);
    }

    @Test
    void rejectsNonHttpBaseUrl() {
        assertThatThrownBy(() -> new TargetGuard(URI.create("file:///etc/passwd"), List.of("/")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
