package com.auraops.operator.application;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.stereotype.Component;

@Component
public class HealingRateLimiter {

    private final io.github.resilience4j.ratelimiter.RateLimiter rateLimiter;

    public HealingRateLimiter(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiter = rateLimiterRegistry.rateLimiter("healingActions");
    }

    public boolean tryAcquire() {
        return rateLimiter.acquirePermission();
    }
}
