package com.ratelimiter.model;

import java.util.function.LongSupplier;

/**
 * Continuous token bucket. Tokens refill smoothly over the configured period,
 * up to {@code capacity}. Each successful request spends one token.
 *
 * <p>All mutations are synchronized on this instance. Callers keep one bucket
 * per client inside a {@link java.util.concurrent.ConcurrentHashMap}.
 */
public final class TokenBucket {

    private final int capacity;
    private final int refillTokens;
    private final long refillPeriodNanos;
    private final LongSupplier nanoTime;

    private double availableTokens;
    private long lastRefillNanos;

    public TokenBucket(int capacity, int refillTokens, long refillPeriodNanos, LongSupplier nanoTime) {
        if (capacity < 1 || refillTokens < 1 || refillPeriodNanos < 1) {
            throw new IllegalArgumentException("capacity, refillTokens, and refillPeriodNanos must be positive");
        }
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = refillPeriodNanos;
        this.nanoTime = nanoTime;
        this.availableTokens = capacity;
        this.lastRefillNanos = nanoTime.getAsLong();
    }

    public synchronized RateLimitDecision tryConsume(String clientKey) {
        refill();
        if (availableTokens >= 1.0d) {
            availableTokens -= 1.0d;
            return new RateLimitDecision(true, capacity, remaining(), 0L, clientKey);
        }
        return new RateLimitDecision(false, capacity, remaining(), retryAfterSeconds(), clientKey);
    }

    private void refill() {
        long now = nanoTime.getAsLong();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0L) {
            return;
        }
        double tokensToAdd = ((double) elapsed / (double) refillPeriodNanos) * refillTokens;
        availableTokens = Math.min(capacity, availableTokens + tokensToAdd);
        lastRefillNanos = now;
    }

    private long remaining() {
        return (long) Math.floor(availableTokens);
    }

    private long retryAfterSeconds() {
        double deficit = 1.0d - availableTokens;
        double periodSeconds = refillPeriodNanos / 1_000_000_000d;
        double seconds = deficit * periodSeconds / refillTokens;
        long retryAfter = (long) Math.ceil(seconds);
        return Math.max(1L, retryAfter);
    }
}
