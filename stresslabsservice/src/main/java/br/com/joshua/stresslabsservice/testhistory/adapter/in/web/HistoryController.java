package br.com.joshua.stresslabsservice.testhistory.adapter.in.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.joshua.stresslabsservice.loadtest.adapter.in.web.ExecutionResponse;
import br.com.joshua.stresslabsservice.metrics.adapter.in.web.ResultResponse;
import br.com.joshua.stresslabsservice.metrics.domain.MetricSnapshot;
import br.com.joshua.stresslabsservice.shared.domain.PageResult;
import br.com.joshua.stresslabsservice.testhistory.application.HistoryService;
import br.com.joshua.stresslabsservice.testhistory.application.HistoryService.ExecutionDetail;
import br.com.joshua.stresslabsservice.testhistory.domain.ComparisonMetric;

@RestController
@RequestMapping("/api/admin/history")
public class HistoryController {

    private final HistoryService service;

    public HistoryController(HistoryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<SummaryResponse> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size).map(summary -> new SummaryResponse(
                ExecutionResponse.from(summary.execution()), ResultResponse.from(summary.result())));
    }

    @GetMapping("/{id}")
    public DetailResponse detail(@PathVariable UUID id) {
        return DetailResponse.from(service.detail(id));
    }

    @GetMapping("/compare")
    public ComparisonResponse compare(@RequestParam UUID a, @RequestParam UUID b) {
        var comparison = service.compare(a, b);
        return new ComparisonResponse(DetailResponse.from(comparison.a()), DetailResponse.from(comparison.b()),
                comparison.metrics());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ClearResponse clear() {
        return new ClearResponse(service.clear());
    }

    public record SummaryResponse(ExecutionResponse execution, ResultResponse result) {
    }

    public record DetailResponse(ExecutionResponse execution, ResultResponse result, List<MetricSnapshot> snapshots) {
        static DetailResponse from(ExecutionDetail detail) {
            return new DetailResponse(ExecutionResponse.from(detail.execution()), ResultResponse.from(detail.result()),
                    detail.snapshots());
        }
    }

    public record ComparisonResponse(DetailResponse a, DetailResponse b, List<ComparisonMetric> metrics) {
    }

    public record ClearResponse(long deleted) {
    }
}
