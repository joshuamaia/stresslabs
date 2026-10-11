package br.com.joshua.stresslabsservice.loadtest.domain;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import br.com.joshua.stresslabsservice.shared.domain.DomainValidationException;

/**
 * Configuração validada de um teste de carga.
 * targetRps (taxa de envio) e concurrency (requisições simultâneas) são grandezas distintas:
 * a taxa é perseguida pelo gerador, mas nunca ultrapassa o limite de concorrência.
 */
public record LoadTestConfig(
        String name,
        TestScenario scenario,
        AllowedTarget target,
        TargetHttpMethod method,
        int totalRequests,
        int concurrency,
        int targetRps,
        int durationSeconds,
        int timeoutMs,
        boolean rateLimitEnabled,
        String requestBody,
        Map<String, String> headers) {

    public static final Set<String> ALLOWED_HEADERS = Set.of("X-Client-Id", "Accept", "X-Request-Tag");
    public static final int MAX_BODY_LENGTH = 4096;
    private static final Pattern SAFE_HEADER_VALUE = Pattern.compile("[\\x20-\\x7E]{1,128}");

    public LoadTestConfig {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static LoadTestConfig create(String name, TestScenario scenario, AllowedTarget target,
                                        TargetHttpMethod method, int totalRequests, int concurrency, int targetRps,
                                        int durationSeconds, int timeoutMs, boolean rateLimitEnabled,
                                        String requestBody, Map<String, String> headers, LoadTestLimits limits) {
        require(scenario != null, "Tipo de teste é obrigatório");
        require(target != null, "Endpoint não autorizado");
        TargetHttpMethod effectiveMethod = method == null ? target.method() : method;
        require(effectiveMethod == target.method(),
                "Método " + effectiveMethod + " não permitido para o endpoint " + target.key());
        requireRange(totalRequests, 1, limits.maxRequests(), "Quantidade de requisições");
        requireRange(concurrency, 1, limits.maxConcurrency(), "Concorrência");
        requireRange(targetRps, 1, limits.maxRps(), "Taxa de envio (RPS)");
        requireRange(durationSeconds, 1, limits.maxDurationSeconds(), "Duração (s)");
        requireRange(timeoutMs, limits.minTimeoutMs(), limits.maxTimeoutMs(), "Timeout (ms)");

        String body = null;
        if (effectiveMethod == TargetHttpMethod.POST) {
            body = requestBody == null || requestBody.isBlank() ? target.defaultBody() : requestBody.trim();
            require(body != null && body.length() <= MAX_BODY_LENGTH,
                    "Corpo JSON obrigatório e limitado a " + MAX_BODY_LENGTH + " caracteres");
            require(body.startsWith("{") && body.endsWith("}"), "Corpo deve ser um objeto JSON");
        }
        if (headers != null) {
            headers.forEach((key, value) -> {
                require(ALLOWED_HEADERS.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(key)),
                        "Header não permitido: " + key + ". Permitidos: " + ALLOWED_HEADERS);
                require(value != null && SAFE_HEADER_VALUE.matcher(value).matches(),
                        "Valor inválido para o header " + key);
            });
        }
        String effectiveName = name == null || name.isBlank()
                ? scenario + " - " + target.key()
                : name.trim();
        require(effectiveName.length() <= 120, "Nome deve ter até 120 caracteres");

        return new LoadTestConfig(effectiveName, scenario, target, effectiveMethod, totalRequests, concurrency,
                targetRps, durationSeconds, timeoutMs, rateLimitEnabled, body, headers);
    }

    private static void requireRange(int value, int min, int max, String field) {
        require(value >= min && value <= max, field + " deve estar entre " + min + " e " + max);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainValidationException(message);
        }
    }
}
