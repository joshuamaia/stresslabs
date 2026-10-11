package br.com.joshua.stresslabsservice.loadtest.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import br.com.joshua.stresslabsservice.loadtest.adapter.out.sse.SseLiveUpdateHub;
import br.com.joshua.stresslabsservice.loadtest.application.LoadTestService;
import br.com.joshua.stresslabsservice.loadtest.application.StartLoadTestCommand;
import br.com.joshua.stresslabsservice.loadtest.domain.AllowedTarget;
import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestConfig;
import br.com.joshua.stresslabsservice.loadtest.domain.LoadTestLimits;
import br.com.joshua.stresslabsservice.loadtest.domain.TargetHttpMethod;
import br.com.joshua.stresslabsservice.loadtest.domain.TestScenario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/admin/load-tests")
public class LoadTestController {

    private final LoadTestService service;
    private final SseLiveUpdateHub liveUpdates;

    public LoadTestController(LoadTestService service, SseLiveUpdateHub liveUpdates) {
        this.service = service;
        this.liveUpdates = liveUpdates;
    }

    @GetMapping("/options")
    public OptionsResponse options() {
        List<TargetResponse> targets = service.targets().stream().map(TargetResponse::from).toList();
        return new OptionsResponse(targets, List.of(TestScenario.values()), service.limits(),
                LoadTestConfig.ALLOWED_HEADERS.stream().sorted().toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionResponse start(@Valid @RequestBody StartLoadTestRequest request) {
        var command = new StartLoadTestCommand(request.name(), request.scenario(), request.targetKey(),
                request.method(), request.totalRequests(), request.concurrency(), request.targetRps(),
                request.durationSeconds(), request.timeoutMs(), request.rateLimitEnabled(), request.requestBody(),
                request.headers());
        return ExecutionResponse.from(service.start(command));
    }

    @GetMapping("/active")
    public ResponseEntity<ExecutionResponse> active() {
        return service.active()
                .map(ExecutionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public ExecutionResponse get(@PathVariable UUID id) {
        return ExecutionResponse.from(service.get(id));
    }

    @PostMapping("/{id}/stop")
    public ExecutionResponse stop(@PathVariable UUID id) {
        return ExecutionResponse.from(service.stop(id));
    }

    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID id) {
        return liveUpdates.subscribe(service.get(id));
    }

    public record StartLoadTestRequest(
            @Size(max = 120) String name,
            @NotNull TestScenario scenario,
            @NotBlank String targetKey,
            TargetHttpMethod method,
            int totalRequests,
            int concurrency,
            int targetRps,
            int durationSeconds,
            int timeoutMs,
            boolean rateLimitEnabled,
            String requestBody,
            Map<String, String> headers) {
    }

    public record TargetResponse(String key, String label, TargetHttpMethod method, String path,
                                 String description, String defaultBody) {
        static TargetResponse from(AllowedTarget target) {
            return new TargetResponse(target.key(), target.label(), target.method(), target.path(),
                    target.description(), target.defaultBody());
        }
    }

    public record OptionsResponse(List<TargetResponse> targets, List<TestScenario> scenarios, LoadTestLimits limits,
                                  List<String> allowedHeaders) {
    }
}
