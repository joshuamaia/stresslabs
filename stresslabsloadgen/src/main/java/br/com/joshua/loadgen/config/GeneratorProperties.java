package br.com.joshua.loadgen.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loadgen")
public record GeneratorProperties(
        URI targetBaseUrl,
        List<String> allowedPathPrefixes,
        URI backendUrl,
        String internalToken,
        Duration snapshotInterval,
        Limits limits) {

    public record Limits(int maxRequests, int maxConcurrency, int maxDurationSeconds, int maxRps, int minTimeoutMs,
                         int maxTimeoutMs) {
    }
}
