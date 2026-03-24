package com.auraops.analyzer.domain.model;

public record Incident(
    String incidentId,
    Resource resource,
    Telemetry telemetry
) {
    public Incident {
        if (incidentId == null || incidentId.isBlank()) {
            throw new IllegalArgumentException("incidentId cannot be blank");
        }
        if (resource == null) {
            throw new IllegalArgumentException("resource cannot be null");
        }
        if (telemetry == null) {
            throw new IllegalArgumentException("telemetry cannot be null");
        }
    }
}
