package br.com.joshua.loadgen.report;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.com.joshua.loadgen.config.GeneratorProperties;
import br.com.joshua.loadgen.execution.ProgressListener;
import br.com.joshua.loadgen.metrics.FinalReport;
import br.com.joshua.loadgen.metrics.Snapshot;
import br.com.joshua.loadgen.security.InternalTokenFilter;

/** Envia snapshots periódicos e o resultado consolidado ao backend. */
@Component
public class BackendReporter implements ProgressListener {

    static final int RESULT_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(BackendReporter.class);

    private final RestClient client;

    public BackendReporter(RestClient.Builder builder, GeneratorProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.client = builder
                .baseUrl(properties.backendUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader(InternalTokenFilter.HEADER, properties.internalToken())
                .build();
    }

    @Override
    public boolean onSnapshot(UUID executionId, Snapshot snapshot) {
        try {
            client.post().uri("/api/internal/load-tests/{id}/snapshots", executionId).body(snapshot)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            // 410: execução encerrada no backend; 404: desconhecida
            boolean rejected = e.getStatusCode() == HttpStatus.GONE || e.getStatusCode() == HttpStatus.NOT_FOUND;
            log.warn("Snapshot rejeitado ({}): {}", e.getStatusCode().value(), executionId);
            return !rejected;
        } catch (RestClientException e) {
            log.warn("Backend indisponível para snapshot de {}: {}", executionId, e.getMessage());
            return true;
        }
    }

    @Override
    public void onFinished(UUID executionId, FinalReport report) {
        for (int attempt = 1; attempt <= RESULT_ATTEMPTS; attempt++) {
            try {
                client.post().uri("/api/internal/load-tests/{id}/result", executionId).body(report)
                        .retrieve().toBodilessEntity();
                return;
            } catch (HttpClientErrorException e) {
                log.error("Resultado rejeitado pelo backend ({}): {}", e.getStatusCode().value(), executionId);
                return;
            } catch (RestClientException e) {
                log.warn("Tentativa {}/{} de envio do resultado falhou: {}", attempt, RESULT_ATTEMPTS,
                        e.getMessage());
                sleep(Duration.ofSeconds(attempt));
            }
        }
        log.error("Resultado da execução {} não pôde ser entregue ao backend", executionId);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
