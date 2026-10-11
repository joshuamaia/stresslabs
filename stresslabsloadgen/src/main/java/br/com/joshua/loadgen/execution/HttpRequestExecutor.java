package br.com.joshua.loadgen.execution;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import br.com.joshua.loadgen.metrics.MetricsCollector;
import br.com.joshua.loadgen.metrics.Outcome;
import br.com.joshua.loadgen.security.TargetGuard;

/** Executa uma requisição HTTP real, mede a latência (relógio monotônico) e classifica o resultado. */
public class HttpRequestExecutor implements AutoCloseable {

    static final Duration MAX_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client;
    private final TargetGuard guard;
    private final RunPlan plan;
    private final Duration requestTimeout;

    public HttpRequestExecutor(RunPlan plan, TargetGuard guard) {
        this.plan = plan;
        this.guard = guard;
        this.requestTimeout = Duration.ofMillis(plan.timeoutMs());
        Duration connectTimeout = requestTimeout.compareTo(MAX_CONNECT_TIMEOUT) < 0 ? requestTimeout
                : MAX_CONNECT_TIMEOUT;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public Outcome execute(MetricsCollector metrics) {
        HttpRequest request = buildRequest();
        metrics.onRequestStart();
        long start = System.nanoTime();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long latency = System.nanoTime() - start;
            metrics.onResponse(response.statusCode(), latency);
            return Outcome.fromStatus(response.statusCode());
        } catch (HttpConnectTimeoutException e) {
            return fail(metrics, Outcome.CONNECTION_FAILURE);
        } catch (HttpTimeoutException e) {
            return fail(metrics, Outcome.TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(metrics, Outcome.CANCELLED);
        } catch (IOException e) {
            return fail(metrics, Thread.currentThread().isInterrupted() ? Outcome.CANCELLED
                    : Outcome.CONNECTION_FAILURE);
        } finally {
            metrics.onRequestEnd();
        }
    }

    HttpRequest buildRequest() {
        long id = plan.idRange() > 0 ? ThreadLocalRandom.current().nextLong(1, plan.idRange() + 1L) : 0;
        URI uri = guard.resolve(plan.path(), id);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(requestTimeout);
        plan.headers().forEach(builder::header);
        if (plan.isPost()) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(plan.body() == null ? "{}" : plan.body()));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private static Outcome fail(MetricsCollector metrics, Outcome outcome) {
        metrics.onFailure(outcome);
        return outcome;
    }

    @Override
    public void close() {
        client.close();
    }
}
