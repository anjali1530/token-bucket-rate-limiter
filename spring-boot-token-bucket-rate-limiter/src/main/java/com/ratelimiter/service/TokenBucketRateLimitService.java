package com.ratelimiter.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.ratelimiter.config.RateLimitProperties;
import com.ratelimiter.model.RateLimitDecision;
import com.ratelimiter.model.TokenBucket;

/**
 * In-memory stand-in for the Redis counter used by fastapi-redis-rate-limiter.
 * One {@link TokenBucket} is stored per client key. No external server is required.
 */
@Service
public class TokenBucketRateLimitService {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;
    private final NanoClock clock;
    private final long refillPeriodNanos;

    public TokenBucketRateLimitService(RateLimitProperties properties, NanoClock clock) {
        this.properties = properties;
        this.clock = clock;
        this.refillPeriodNanos = Duration.ofSeconds(properties.getRefillPeriodSeconds()).toNanos();
    }

    public RateLimitDecision consume(String clientKey) {
        TokenBucket bucket = buckets.computeIfAbsent(clientKey, key -> new TokenBucket(
                properties.getCapacity(),
                properties.getRefillTokens(),
                refillPeriodNanos,
                clock::nanoTime));
        return bucket.tryConsume(clientKey);
    }

    public int trackedClients() {
        return buckets.size();
    }
}
