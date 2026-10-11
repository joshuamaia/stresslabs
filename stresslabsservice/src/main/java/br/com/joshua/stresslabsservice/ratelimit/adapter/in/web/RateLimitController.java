package br.com.joshua.stresslabsservice.ratelimit.adapter.in.web;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.joshua.stresslabsservice.ratelimit.application.RateLimitService;
import br.com.joshua.stresslabsservice.ratelimit.application.RateLimitService.UpdateRateLimitCommand;
import br.com.joshua.stresslabsservice.ratelimit.domain.ClientIdentificationStrategy;
import br.com.joshua.stresslabsservice.ratelimit.domain.RateLimitPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/admin/rate-limit")
public class RateLimitController {

    private final RateLimitService service;

    public RateLimitController(RateLimitService service) {
        this.service = service;
    }

    @GetMapping
    public RateLimitResponse get() {
        return RateLimitResponse.from(service.currentPolicy());
    }

    @PutMapping
    public RateLimitResponse update(@Valid @RequestBody RateLimitRequest request) {
        var command = new UpdateRateLimitCommand(request.enabled(), request.capacity(), request.refillTokens(),
                request.refillPeriodSeconds(), request.clientStrategy());
        return RateLimitResponse.from(service.update(command));
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        service.resetBuckets();
        return ResponseEntity.noContent().build();
    }

    public record RateLimitRequest(
            boolean enabled,
            @Min(1) @Max(RateLimitPolicy.MAX_CAPACITY) long capacity,
            @Min(1) @Max(RateLimitPolicy.MAX_CAPACITY) long refillTokens,
            @Min(1) @Max(3600) long refillPeriodSeconds,
            @NotNull ClientIdentificationStrategy clientStrategy) {
    }

    public record RateLimitResponse(boolean enabled, long capacity, long refillTokens, long refillPeriodSeconds,
                                    ClientIdentificationStrategy clientStrategy, Instant updatedAt, String summary) {
        static RateLimitResponse from(RateLimitPolicy policy) {
            return new RateLimitResponse(policy.enabled(), policy.capacity(), policy.refillTokens(),
                    policy.refillPeriod().toSeconds(), policy.clientStrategy(), policy.updatedAt(), policy.summary());
        }
    }
}
