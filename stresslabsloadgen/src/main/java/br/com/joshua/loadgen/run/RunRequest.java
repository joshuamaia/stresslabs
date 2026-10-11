package br.com.joshua.loadgen.run;

import java.util.Map;
import java.util.UUID;

import br.com.joshua.loadgen.config.GeneratorProperties.Limits;
import br.com.joshua.loadgen.execution.RunPlan;
import br.com.joshua.loadgen.execution.Scenario;
import br.com.joshua.loadgen.security.TargetGuard;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RunRequest(
        @NotNull UUID executionId,
        @NotNull Scenario scenario,
        @NotNull @Pattern(regexp = "GET|POST") String method,
        @NotNull String path,
        int idRange,
        String body,
        Map<String, String> headers,
        int totalRequests,
        int concurrency,
        int targetRps,
        int durationSeconds,
        int timeoutMs) {

    /** Revalida limites e destino de forma independente do backend (defesa em profundidade). */
    public RunPlan toPlan(TargetGuard guard, Limits limits) {
        requireRange(totalRequests, 1, limits.maxRequests(), "totalRequests");
        requireRange(concurrency, 1, limits.maxConcurrency(), "concurrency");
        requireRange(targetRps, 1, limits.maxRps(), "targetRps");
        requireRange(durationSeconds, 1, limits.maxDurationSeconds(), "durationSeconds");
        requireRange(timeoutMs, limits.minTimeoutMs(), limits.maxTimeoutMs(), "timeoutMs");
        requireRange(idRange, 0, 10_000_000, "idRange");
        if (body != null && body.length() > 4096) {
            throw new IllegalArgumentException("body excede 4096 caracteres");
        }
        String validatedPath = guard.validatePath(path);
        guard.validateHeaders(headers);
        return new RunPlan(executionId, scenario, method, validatedPath, idRange, body, headers, totalRequests,
                concurrency, targetRps, durationSeconds, timeoutMs);
    }

    private static void requireRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " deve estar entre " + min + " e " + max);
        }
    }
}
