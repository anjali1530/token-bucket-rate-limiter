package com.ratelimiter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.ratelimiter.config.RateLimitProperties;
import com.ratelimiter.model.RateLimitDecision;

class TokenBucketRateLimitServiceTest {

    @Test
    void allowsBurstThenRefillsOverThePeriod() {
        AtomicLong nanos = new AtomicLong(0L);
        TokenBucketRateLimitService service = service(10, 10, 10, nanos::get);

        for (int i = 0; i < 10; i++) {
            RateLimitDecision decision = service.consume("10.0.0.8");
            assertTrue(decision.allowed());
            assertEquals(9L - i, decision.remaining());
        }

        RateLimitDecision denied = service.consume("10.0.0.8");
        assertFalse(denied.allowed());
        assertEquals(0L, denied.remaining());
        assertEquals(1L, denied.retryAfterSeconds());

        nanos.addAndGet(Duration.ofSeconds(5).toNanos());
        int allowedAfterHalfPeriod = 0;
        for (int i = 0; i < 10; i++) {
            if (service.consume("10.0.0.8").allowed()) {
                allowedAfterHalfPeriod++;
            }
        }
        assertEquals(5, allowedAfterHalfPeriod);
    }

    @Test
    void isolatesClients() {
        TokenBucketRateLimitService service = service(1, 1, 60, () -> 0L);

        assertTrue(service.consume("10.0.0.1").allowed());
        assertFalse(service.consume("10.0.0.1").allowed());
        assertTrue(service.consume("10.0.0.2").allowed());
        assertEquals(2, service.trackedClients());
    }

    @Test
    void concurrentConsumesNeverExceedCapacity() throws Exception {
        int capacity = 50;
        TokenBucketRateLimitService service = service(capacity, capacity, 3600, () -> 0L);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                tasks.add(() -> {
                    int allowed = 0;
                    for (int n = 0; n < 20; n++) {
                        if (service.consume("shared-client").allowed()) {
                            allowed++;
                        }
                    }
                    return allowed;
                });
            }
            int totalAllowed = 0;
            for (Future<Integer> future : pool.invokeAll(tasks)) {
                totalAllowed += future.get();
            }
            assertEquals(capacity, totalAllowed);
        } finally {
            pool.shutdownNow();
        }
    }

    private static TokenBucketRateLimitService service(
            int capacity,
            int refillTokens,
            int refillPeriodSeconds,
            NanoClock clock) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCapacity(capacity);
        properties.setRefillTokens(refillTokens);
        properties.setRefillPeriodSeconds(refillPeriodSeconds);
        return new TokenBucketRateLimitService(properties, clock);
    }
}
