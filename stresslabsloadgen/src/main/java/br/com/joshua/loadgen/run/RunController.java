package br.com.joshua.loadgen.run;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.joshua.loadgen.config.GeneratorProperties;
import br.com.joshua.loadgen.security.TargetGuard;
import br.com.joshua.loadgen.security.TargetGuard.TargetNotAllowedException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunManager runManager;
    private final TargetGuard guard;
    private final GeneratorProperties properties;

    public RunController(RunManager runManager, TargetGuard guard, GeneratorProperties properties) {
        this.runManager = runManager;
        this.guard = guard;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> start(@Valid @RequestBody RunRequest request) {
        var run = runManager.start(request.toPlan(guard, properties.limits()));
        return ResponseEntity.accepted().body(Map.of("executionId", run.executionId(), "status", "ACCEPTED"));
    }

    @PostMapping("/{executionId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID executionId) {
        return runManager.cancel(executionId) ? ResponseEntity.accepted().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> active() {
        return runManager.active()
                .<ResponseEntity<Map<String, Object>>>map(run -> ResponseEntity.ok(
                        Map.of("executionId", run.executionId())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @ExceptionHandler({IllegalArgumentException.class, TargetNotAllowedException.class})
    ProblemDetail badRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RunConflictException.class)
    ProblemDetail conflict(RunConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
