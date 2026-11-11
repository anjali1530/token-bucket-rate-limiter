package com.ratelimiter.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    @Min(1)
    private int capacity = 40;

    @Min(1)
    private int refillTokens = 40;

    @Min(1)
    private int refillPeriodSeconds = 60;

    private List<String> includePathPatterns = new ArrayList<>(List.of("/api/**"));

    private List<String> excludePathPatterns = new ArrayList<>(List.of("/api/health"));

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getRefillTokens() {
        return refillTokens;
    }

    public void setRefillTokens(int refillTokens) {
        this.refillTokens = refillTokens;
    }

    public int getRefillPeriodSeconds() {
        return refillPeriodSeconds;
    }

    public void setRefillPeriodSeconds(int refillPeriodSeconds) {
        this.refillPeriodSeconds = refillPeriodSeconds;
    }

    public List<String> getIncludePathPatterns() {
        return includePathPatterns;
    }

    public void setIncludePathPatterns(List<String> includePathPatterns) {
        this.includePathPatterns = includePathPatterns;
    }

    public List<String> getExcludePathPatterns() {
        return excludePathPatterns;
    }

    public void setExcludePathPatterns(List<String> excludePathPatterns) {
        this.excludePathPatterns = excludePathPatterns;
    }
}
