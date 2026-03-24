package com.auraops.analyzer.domain.model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisResultTest {

    @Test
    void success_shouldEnforceInvariants() {
        RemediationAction action = new RemediationAction("RESTART", Map.of());
        
        assertThrows(IllegalArgumentException.class, () ->
            new AnalysisResult.Success("", "diag", 0.9, action, "reason"));

        assertThrows(IllegalArgumentException.class, () -> 
            new AnalysisResult.Success("id", "", 0.9, action, "reason"));
        
        assertThrows(IllegalArgumentException.class, () -> 
            new AnalysisResult.Success("id", "diag", -0.1, action, "reason"));

        assertThrows(IllegalArgumentException.class, () ->
            new AnalysisResult.Success("id", "diag", 1.1, action, "reason"));
        
        assertThrows(IllegalArgumentException.class, () -> 
            new AnalysisResult.Success("id", "diag", 0.9, null, "reason"));

        assertThrows(IllegalArgumentException.class, () ->
            new AnalysisResult.Success("id", "diag", 0.9, action, ""));
    }

    @Test
    void inconclusive_shouldHaveDeepImmutability() {
        List<String> mutableList = new ArrayList<>();
        mutableList.add("logs");
        
        AnalysisResult.Inconclusive result = new AnalysisResult.Inconclusive("id", "reason", mutableList);
        
        mutableList.add("metrics");
        
        assertEquals(1, result.missingDataPoints().size(), "Record should not be affected by external list modification");
        assertThrows(UnsupportedOperationException.class, () -> 
            result.missingDataPoints().add("new"), "Record list should be immutable");
    }

    @Test
    void inconclusive_shouldEnforceInvariants() {
        assertThrows(IllegalArgumentException.class, () ->
            new AnalysisResult.Inconclusive("", "reason", List.of()));

        assertThrows(IllegalArgumentException.class, () ->
            new AnalysisResult.Inconclusive("id", "", List.of()));
    }

    @Test
    void criticalFailure_shouldEnforceInvariants() {
        assertThrows(IllegalArgumentException.class, () ->
            new AnalysisResult.CriticalFailure("", ErrorCode.PARSING_ERROR, "message"));

        assertThrows(IllegalArgumentException.class, () ->
            new AnalysisResult.CriticalFailure("id", null, "message"));

        assertThrows(IllegalArgumentException.class, () ->
            new AnalysisResult.CriticalFailure("id", ErrorCode.PARSING_ERROR, ""));
    }
}
