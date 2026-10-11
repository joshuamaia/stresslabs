package br.com.joshua.stresslabsservice.loadtest.application.port.out;

import java.util.UUID;

import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;

/** Comunicação com o gerador de carga (processo separado). */
public interface LoadGeneratorPort {

    /** Solicita o início da execução. Lança ExternalServiceException se o gerador recusar ou estiver indisponível. */
    void start(TestExecution execution);

    /** Solicita o cancelamento. Retorna false se o gerador não conhece/não está executando o teste. */
    boolean cancel(UUID executionId);
}
