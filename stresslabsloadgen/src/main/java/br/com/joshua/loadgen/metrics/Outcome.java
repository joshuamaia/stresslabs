package br.com.joshua.loadgen.metrics;

/** Classificação do resultado de uma requisição. */
public enum Outcome {
    /** Resposta 2xx/3xx. */
    SUCCESS,
    /** HTTP 429 (Rate Limiting). */
    BLOCKED,
    /** Demais respostas 4xx/5xx. */
    HTTP_ERROR,
    /** Recusa/queda de conexão ou timeout de conexão. */
    CONNECTION_FAILURE,
    /** Sem resposta dentro do timeout configurado. */
    TIMEOUT,
    /** Interrompida pelo cancelamento do teste (não contabilizada como erro). */
    CANCELLED;

    public static Outcome fromStatus(int status) {
        if (status == 429) {
            return BLOCKED;
        }
        return status >= 200 && status < 400 ? SUCCESS : HTTP_ERROR;
    }
}
