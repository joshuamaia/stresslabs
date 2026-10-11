package br.com.joshua.stresslabsservice.loadtest.adapter.out.generator;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import br.com.joshua.stresslabsservice.loadtest.application.port.out.LoadGeneratorPort;
import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestConfig;
import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.shared.config.LabProperties;
import br.com.joshua.stresslabsservice.shared.domain.ExternalServiceException;

/** Cliente HTTP do gerador de carga. Apenas o caminho relativo do destino é enviado, nunca uma URL. */
@Component
class RestLoadGeneratorAdapter implements LoadGeneratorPort {

    static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient client;

    RestLoadGeneratorAdapter(RestClient.Builder builder, LabProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.loadGenerator().connectTimeout());
        requestFactory.setReadTimeout(properties.loadGenerator().readTimeout());
        this.client = builder
                .baseUrl(properties.loadGenerator().url())
                .requestFactory(requestFactory)
                .defaultHeader(TOKEN_HEADER, properties.internalToken())
                .build();
    }

    @Override
    public void start(TestExecution execution) {
        LoadTestConfig config = execution.config();
        var request = new GeneratorRunRequest(execution.id(), config.scenario().name(), config.method().name(),
                config.target().path(), config.target().idRange(), config.requestBody(), config.headers(),
                config.totalRequests(), config.concurrency(), config.targetRps(), config.durationSeconds(),
                config.timeoutMs());
        try {
            client.post().uri("/api/runs").body(request).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new ExternalServiceException("Gerador recusou o teste (HTTP " + e.getStatusCode().value() + "): "
                    + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Gerador de carga inacessível: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean cancel(UUID executionId) {
        try {
            client.post().uri("/api/runs/{id}/cancel", executionId).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw new ExternalServiceException("Falha ao cancelar no gerador: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Gerador de carga inacessível: " + e.getMessage(), e);
        }
    }

    record GeneratorRunRequest(UUID executionId, String scenario, String method, String path, int idRange,
                               String body, Map<String, String> headers, int totalRequests, int concurrency,
                               int targetRps, int durationSeconds, int timeoutMs) {
    }
}
