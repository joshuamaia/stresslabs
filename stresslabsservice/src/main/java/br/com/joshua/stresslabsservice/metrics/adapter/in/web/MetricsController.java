package br.com.joshua.stresslabsservice.metrics.adapter.in.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.joshua.stresslabsservice.metrics.application.MetricsQueryService;
import br.com.joshua.stresslabsservice.metrics.domain.InfrastructureMetrics;
import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;

@RestController
@RequestMapping("/api/admin/metrics")
public class MetricsController {

    private final MetricsQueryService service;

    public MetricsController(MetricsQueryService service) {
        this.service = service;
    }

    @GetMapping("/infrastructure")
    public InfrastructureMetrics infrastructure() {
        return service.infrastructure();
    }

    @GetMapping("/executions/{executionId}/snapshots")
    public List<MetricSnapshot> snapshots(@PathVariable UUID executionId) {
        return service.snapshots(executionId);
    }

    @GetMapping("/executions/{executionId}/result")
    public ResponseEntity<ResultResponse> result(@PathVariable UUID executionId) {
        return service.result(executionId)
                .map(ResultResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
