package br.com.joshua.stresslabsservice.ratelimit.domain;

public record RateLimitDecision(boolean allowed, long limit, long remaining, long retryAfterSeconds) {

    public static RateLimitDecision unlimited() {
        return new RateLimitDecision(true, -1, -1, 0);
    }

    public boolean limited() {
        return limit >= 0;
    }
}
