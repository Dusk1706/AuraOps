package com.auraops.analyzer.domain.model;

import java.util.Map;

public record Metrics(
    String memoryUsage,
    String cpuUsage,
    int restartCount,
    Map<String, Object> additionalMetrics
) {
    public Metrics {
        if (restartCount < 0) {
            throw new IllegalArgumentException("restartCount cannot be negative");
        }
        additionalMetrics = Map.copyOf(additionalMetrics == null ? Map.of() : additionalMetrics);
    }

    public Metrics(String memoryUsage, String cpuUsage, int restartCount) {
        this(memoryUsage, cpuUsage, restartCount, Map.of());
    }
}
