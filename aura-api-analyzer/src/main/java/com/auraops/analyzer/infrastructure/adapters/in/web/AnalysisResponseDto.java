package com.auraops.analyzer.infrastructure.adapters.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record AnalysisResponseDto(
    @JsonProperty("diagnosis") String diagnosis,
    @JsonProperty("confidence") double confidence,
    @JsonProperty("recommended_action") RemediationActionDto recommendedAction,
    @JsonProperty("explanation") String explanation
) {
    public record RemediationActionDto(
        @JsonProperty("type") String type,
        @JsonProperty("parameters") Map<String, Object> parameters
    ) {}
}
