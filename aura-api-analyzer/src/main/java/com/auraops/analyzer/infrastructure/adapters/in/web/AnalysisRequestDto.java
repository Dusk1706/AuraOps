package com.auraops.analyzer.infrastructure.adapters.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;

public record AnalysisRequestDto(
    @NotBlank @JsonProperty("incident_id") String incidentId,
    @NotNull @Valid @JsonProperty("resource") ResourceDto resource,
    @NotNull @Valid @JsonProperty("telemetry") TelemetryDto telemetry
) {
    public record ResourceDto(
        @NotBlank @JsonProperty("kind") String kind,
        @NotBlank @JsonProperty("name") String name,
        @JsonProperty("namespace") String namespace
    ) {}

    public record TelemetryDto(
        @NotEmpty @JsonProperty("logs") List<String> logs,
        @NotNull @Valid @JsonProperty("metrics") MetricsDto metrics,
        @JsonProperty("traces") List<String> traces
    ) {}

    public record MetricsDto(
        @JsonProperty("memory_usage") String memoryUsage,
        @JsonProperty("cpu_usage") String cpuUsage,
        @PositiveOrZero @JsonProperty("restart_count") int restartCount,
        @JsonProperty("additional_metrics") Map<String, Object> additionalMetrics
    ) {}
}
