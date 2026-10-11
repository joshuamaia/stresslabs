package br.com.joshua.stresslabsservice.loadtest.domain;

public enum TestScenario {
    /** Mantém a taxa de envio (RPS) configurada durante todo o teste. */
    CONSTANT,
    /** Aumenta gradualmente de 10% até 100% da taxa configurada. */
    RAMP_UP,
    /** Taxa base de 20%, pico repentino de 100% no meio do teste e retorno à base. */
    SPIKE,
    /** Envia requisições acima do limite configurado para validar o HTTP 429. */
    RATE_LIMIT
}
