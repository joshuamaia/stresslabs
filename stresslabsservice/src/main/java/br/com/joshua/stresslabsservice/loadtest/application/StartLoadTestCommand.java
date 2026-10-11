package br.com.joshua.stresslabsservice.loadtest.application;

import java.util.Map;

import br.com.joshua.stresslabsservice.loadtest.domain.TargetHttpMethod;
import br.com.joshua.stresslabsservice.loadtest.domain.TestScenario;

public record StartLoadTestCommand(
        String name,
        TestScenario scenario,
        String targetKey,
        TargetHttpMethod method,
        int totalRequests,
        int concurrency,
        int targetRps,
        int durationSeconds,
        int timeoutMs,
        boolean rateLimitEnabled,
        String requestBody,
        Map<String, String> headers) {
}
