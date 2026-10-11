package br.com.joshua.stresslabsservice.loadtest.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.joshua.stresslabsservice.loadtest.application.ExecutionProgressService;
import br.com.joshua.stresslabsservice.loadtest.domain.TestStatus;
import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;
import br.com.joshua.stresslabsservice.metrics.domain.TestResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Endpoints internos chamados pelo gerador de carga (token verificado pelo InternalTokenFilter). */
@RestController
@RequestMapping("/api/internal/load-tests/{executionId}")
public class GeneratorCallbackController {

    private final ExecutionProgressService progress;

    public GeneratorCallbackController(ExecutionProgressService progress) {
        this.progress = progress;
    }

    @PostMapping("/snapshots")
    public ResponseEntity<Void> snapshot(@PathVariable UUID executionId, @Valid @RequestBody SnapshotPayload payload) {
        boolean accepted = progress.recordSnapshot(payload.toDomain(executionId));
        return accepted ? ResponseEntity.accepted().build() : ResponseEntity.status(HttpStatus.GONE).build();
    }

    @PostMapping("/result")
    public ResponseEntity<Void> result(@PathVariable UUID executionId, @Valid @RequestBody ResultPayload payload) {
        if (!payload.status().isFinal()) {
            return ResponseEntity.badRequest().build();
        }
        progress.recordResult(payload.toDomain(executionId), payload.status(), payload.failureReason());
        return ResponseEntity.accepted().build();
    }

    public record SnapshotPayload(@NotNull Instant timestamp, long elapsedMs, double currentRps, double targetRps,
                                  int inFlight, int maxInFlight, double avgLatencyMs, double p95LatencyMs,
                                  double p99LatencyMs, long sent, long completed, long success, long blocked,
                                  long httpErrors, long connectionFailures, long timeouts, long status2xx,
                                  long status4xx, long status5xx, double progress) {
        MetricSnapshot toDomain(UUID executionId) {
            return new MetricSnapshot(executionId, timestamp, elapsedMs, currentRps, targetRps, inFlight,
                    maxInFlight, avgLatencyMs, p95LatencyMs, p99LatencyMs, sent, completed, success,
                    httpErrors + connectionFailures + timeouts, blocked, timeouts, connectionFailures, status2xx,
                    status4xx, status5xx, Math.clamp(progress, 0.0, 1.0));
        }
    }

    public record ResultPayload(@NotNull TestStatus status, String failureReason, long totalRequests,
                                long completed, long success, long blocked, long httpErrors,
                                long connectionFailures, long timeouts, long status2xx, long status3xx,
                                long status4xx, long status5xx, double avgRps, double avgLatencyMs,
                                double p95LatencyMs, double p99LatencyMs, double minLatencyMs, double maxLatencyMs,
                                int maxInFlight, long durationMs) {
        TestResult toDomain(UUID executionId) {
            return new TestResult(executionId, totalRequests, completed, success,
                    httpErrors + connectionFailures + timeouts, blocked, httpErrors, connectionFailures, timeouts,
                    status2xx, status3xx, status4xx, status5xx, avgRps, avgLatencyMs, p95LatencyMs, p99LatencyMs,
                    minLatencyMs, maxLatencyMs, maxInFlight, durationMs);
        }
    }
}
