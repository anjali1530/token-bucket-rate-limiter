package com.ratelimiter.model;

public record RateLimitDecision(
        boolean allowed,
        long limit,
        long remaining,
        long retryAfterSeconds,
        String clientKey) {
}
