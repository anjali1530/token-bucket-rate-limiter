package com.ratelimiter.dto;

public record EchoResponse(String text, long limit, long remaining) {
}
