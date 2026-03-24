package com.auraops.analyzer.domain.model;

import java.util.List;

public record Telemetry(
    List<String> logs,
    Metrics metrics,
    List<String> traces
) {
    public Telemetry {
        if (logs == null || logs.isEmpty()) {
            throw new IllegalArgumentException("telemetry logs cannot be empty");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("telemetry metrics cannot be null");
        }
        logs = List.copyOf(logs);
        traces = List.copyOf(traces == null ? List.of() : traces);
    }
}
