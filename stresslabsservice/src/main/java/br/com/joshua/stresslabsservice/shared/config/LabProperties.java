package br.com.joshua.stresslabsservice.shared.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stresslabs")
public record LabProperties(
        LoadGenerator loadGenerator,
        String internalToken,
        Security security,
        Limits limits,
        Cors cors) {

    public record LoadGenerator(String url, Duration connectTimeout, Duration readTimeout) {
    }

    public record Security(boolean adminLocalOnly) {
    }

    public record Limits(int maxRequests, int maxConcurrency, int maxDurationSeconds, int maxRps,
                         int minTimeoutMs, int maxTimeoutMs) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
