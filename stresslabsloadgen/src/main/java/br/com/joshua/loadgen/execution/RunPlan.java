package br.com.joshua.loadgen.execution;

import java.util.Map;
import java.util.UUID;

/** Plano de execução já validado (limites, destino e headers). */
public record RunPlan(
        UUID executionId,
        Scenario scenario,
        String method,
        String path,
        int idRange,
        String body,
        Map<String, String> headers,
        int totalRequests,
        int concurrency,
        int targetRps,
        int durationSeconds,
        int timeoutMs) {

    public RunPlan {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public boolean isPost() {
        return "POST".equals(method);
    }
}
