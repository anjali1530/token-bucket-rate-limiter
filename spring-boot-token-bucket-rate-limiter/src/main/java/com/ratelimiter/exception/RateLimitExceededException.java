package com.ratelimiter.exception;

import com.ratelimiter.model.RateLimitDecision;

public class RateLimitExceededException extends RuntimeException {

    private final long limit;
    private final long remaining;
    private final long retryAfterSeconds;
    private final String path;

    public RateLimitExceededException(RateLimitDecision decision, String path) {
        super("Rate limit exceeded. Try again later.");
        this.limit = decision.limit();
        this.remaining = decision.remaining();
        this.retryAfterSeconds = decision.retryAfterSeconds();
        this.path = path;
    }

    public long getLimit() {
        return limit;
    }

    public long getRemaining() {
        return remaining;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getPath() {
        return path;
    }
}
