package com.auraops.analyzer.domain.model;

import java.util.Map;

public record RemediationAction(String type, Map<String, Object> parameters) {
    public RemediationAction {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("remediation action type cannot be blank");
        }
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
    }
}
