package com.ratelimiter.service;

@FunctionalInterface
public interface NanoClock {

    long nanoTime();
}
