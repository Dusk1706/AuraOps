package com.auraops.operator.application;

import java.util.Map;

public sealed interface AnalyzerDecision permits AnalyzerDecision.Success, AnalyzerDecision.Inconclusive, AnalyzerDecision.Failure {

    record Success(
        String diagnosis,
        double confidence,
        String actionType,
        Map<String, Object> parameters,
        String explanation
    ) implements AnalyzerDecision {}

    record Inconclusive(
        String errorCode,
        String message,
        boolean requiresHuman
    ) implements AnalyzerDecision {}

    record Failure(
        String errorCode,
        String message
    ) implements AnalyzerDecision {}
}
