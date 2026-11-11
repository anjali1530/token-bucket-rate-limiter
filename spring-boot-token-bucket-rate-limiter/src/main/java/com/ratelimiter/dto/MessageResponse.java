package com.ratelimiter.dto;

public record MessageResponse(String message, long limit, long remaining) {
}
