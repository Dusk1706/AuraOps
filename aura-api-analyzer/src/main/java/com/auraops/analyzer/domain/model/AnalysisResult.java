package com.auraops.analyzer.domain.model;

import java.util.List;

public sealed interface AnalysisResult 
    permits AnalysisResult.Success, AnalysisResult.Inconclusive, AnalysisResult.CriticalFailure {
    
    record Success(
        String incidentId,
        String diagnosis,
        double confidence,
        RemediationAction recommendedAction,
        String technicalReasoning
    ) implements AnalysisResult {
        public Success {
            if (incidentId == null || incidentId.isBlank()) throw new IllegalArgumentException("incidentId cannot be blank");
            if (diagnosis == null || diagnosis.isBlank()) throw new IllegalArgumentException("diagnosis cannot be blank");
            if (confidence <= 0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
            if (recommendedAction == null) throw new IllegalArgumentException("recommendedAction cannot be null for Success");
            if (technicalReasoning == null || technicalReasoning.isBlank()) throw new IllegalArgumentException("technicalReasoning cannot be blank");
        }
    }

    record Inconclusive(
        String incidentId,
        String reason,
        List<String> missingDataPoints
    ) implements AnalysisResult {
        public Inconclusive {
            if (incidentId == null || incidentId.isBlank()) throw new IllegalArgumentException("incidentId cannot be blank");
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason cannot be blank");
            missingDataPoints = List.copyOf(missingDataPoints != null ? missingDataPoints : List.of());
        }
    }

    record CriticalFailure(
        String incidentId,
        ErrorCode errorCode,
        String message
    ) implements AnalysisResult {
        public CriticalFailure {
            if (incidentId == null || incidentId.isBlank()) throw new IllegalArgumentException("incidentId cannot be blank");
            if (errorCode == null) throw new IllegalArgumentException("errorCode cannot be null");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("message cannot be blank");
        }
    }
}
