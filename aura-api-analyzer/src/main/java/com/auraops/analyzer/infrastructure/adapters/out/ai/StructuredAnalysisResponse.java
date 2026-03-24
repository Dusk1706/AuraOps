package com.auraops.analyzer.infrastructure.adapters.out.ai;

import java.util.List;
import java.util.Map;

public record StructuredAnalysisResponse(
    String status,
    String diagnosis,
    Double confidence,
    StructuredRemediationAction recommendedAction,
    String technicalReasoning,
    String reason,
    List<String> missingDataPoints
) {
    public record StructuredRemediationAction(
        String type,
        Map<String, Object> parameters
    ) {
    }
}
