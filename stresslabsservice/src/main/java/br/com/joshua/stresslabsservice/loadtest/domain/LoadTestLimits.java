package br.com.joshua.stresslabsservice.loadtest.domain;

/** Limites de segurança aplicados a qualquer configuração de teste. */
public record LoadTestLimits(int maxRequests, int maxConcurrency, int maxDurationSeconds, int maxRps,
                             int minTimeoutMs, int maxTimeoutMs) {
}
