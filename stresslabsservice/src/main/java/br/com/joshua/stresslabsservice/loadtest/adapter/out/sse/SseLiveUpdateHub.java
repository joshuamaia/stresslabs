package br.com.joshua.stresslabsservice.loadtest.adapter.out.sse;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import br.com.joshua.stresslabsservice.loadtest.application.port.out.LiveUpdatePublisherPort;
import br.com.joshua.stresslabsservice.loadtest.domain.TestExecution;
import br.com.joshua.stresslabsservice.loadtest.domain.TestStatus;
import br.com.joshua.stresslabsservice.metrics.adapter.in.web.ResultResponse;
import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;
import br.com.joshua.stresslabsservice.metrics.domain.TestResult;

/**
 * Mantém as conexões SSE por execução. Eventos: "status", "snapshot", "result".
 * Ao finalizar a execução o stream é encerrado.
 */
@Component
public class SseLiveUpdateHub implements LiveUpdatePublisherPort {

    static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);
    private static final Logger log = LoggerFactory.getLogger(SseLiveUpdateHub.class);

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(TestExecution execution) {
        var emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        UUID id = execution.id();
        // Envia o status atual primeiro, permitindo ao cliente sincronizar após reconexão
        if (!send(emitter, "status", StatusEvent.from(execution))) {
            return emitter;
        }
        if (execution.status().isFinal()) {
            emitter.complete();
            return emitter;
        }
        List<SseEmitter> list = emitters.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return emitter;
    }

    @Override
    public void publishStatus(TestExecution execution) {
        broadcast(execution.id(), "status", StatusEvent.from(execution));
        if (execution.status().isFinal()) {
            List<SseEmitter> list = emitters.remove(execution.id());
            if (list != null) {
                list.forEach(SseEmitter::complete);
            }
        }
    }

    @Override
    public void publishSnapshot(MetricSnapshot snapshot) {
        broadcast(snapshot.executionId(), "snapshot", snapshot);
    }

    @Override
    public void publishResult(TestResult result) {
        broadcast(result.executionId(), "result", ResultResponse.from(result));
    }

    /** Heartbeat evita que proxies encerrem conexões ociosas. */
    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        emitters.forEach((id, list) -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }));
    }

    int subscriberCount(UUID executionId) {
        return emitters.getOrDefault(executionId, List.of()).size();
    }

    private void broadcast(UUID executionId, String event, Object payload) {
        List<SseEmitter> list = emitters.get(executionId);
        if (list == null) {
            return;
        }
        list.forEach(emitter -> {
            if (!send(emitter, event, payload)) {
                list.remove(emitter);
            }
        });
    }

    private static boolean send(SseEmitter emitter, String event, Object payload) {
        try {
            // SseEmitter não é thread-safe para envios concorrentes
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(payload));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("Cliente SSE desconectado: {}", e.getMessage());
            emitter.completeWithError(e);
            return false;
        }
    }

    public record StatusEvent(UUID executionId, TestStatus status, String failureReason, Instant startedAt,
                              Instant finishedAt) {
        static StatusEvent from(TestExecution execution) {
            return new StatusEvent(execution.id(), execution.status(), execution.failureReason(),
                    execution.startedAt(), execution.finishedAt());
        }
    }
}
