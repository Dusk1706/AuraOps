package com.auraops.analyzer.infrastructure.adapters.out.ai;

import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisResponseParserTest {

    private AnalysisResponseParser parser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        parser = new AnalysisResponseParser(objectMapper);
    }

    @Test
    void parse_success_withExplanation() {
        String json = """
            {
              "status": "SUCCESS",
              "diagnosis": "Memory Leak",
              "confidence": 0.95,
              "recommendedAction": { "type": "RESTART", "parameters": {} },
              "explanation": "Heap growth detected"
            }
            """;
        
        AnalysisResult result = parser.parse("id", json);
        
        assertTrue(result instanceof AnalysisResult.Success);
        AnalysisResult.Success success = (AnalysisResult.Success) result;
        assertEquals("Memory Leak", success.diagnosis());
        assertEquals("Heap growth detected", success.technicalReasoning());
    }

    @Test
    void parse_inconclusive_whenMissingRecommendedAction() {
        String json = """
            {
              "status": "SUCCESS",
              "diagnosis": "Memory Leak",
              "confidence": 0.95
            }
            """;
        
        AnalysisResult result = parser.parse("id", json);
        
        assertTrue(result instanceof AnalysisResult.Inconclusive);
        AnalysisResult.Inconclusive inc = (AnalysisResult.Inconclusive) result;
        assertTrue(inc.missingDataPoints().contains("recommendedAction"));
    }

    @Test
    void parse_inconclusive_whenMissingTechnicalReasoning() {
        String json = """
            {
              "status": "SUCCESS",
              "diagnosis": "Memory Leak",
              "confidence": 0.95,
              "recommendedAction": { "type": "RESTART", "parameters": {} }
            }
            """;

        AnalysisResult result = parser.parse("id", json);

        assertTrue(result instanceof AnalysisResult.Inconclusive);
        AnalysisResult.Inconclusive inc = (AnalysisResult.Inconclusive) result;
        assertTrue(inc.missingDataPoints().contains("technicalReasoning"));
    }

    @Test
    void parse_success_acceptsSnakeCaseAndDefaultsMissingParameters() {
        String json = """
            {
              "status": "SUCCESS",
              "diagnosis": "Memory Leak",
              "confidence": 0.95,
              "recommended_action": { "type": "RESTART" },
              "technical_reasoning": "Heap growth detected"
            }
            """;

        AnalysisResult result = parser.parse("id", json);

        assertTrue(result instanceof AnalysisResult.Success);
        AnalysisResult.Success success = (AnalysisResult.Success) result;
        assertEquals("RESTART", success.recommendedAction().type());
        assertTrue(success.recommendedAction().parameters().isEmpty());
        assertEquals("Heap growth detected", success.technicalReasoning());
    }

    @Test
    void parse_criticalFailure_onMalformedJson() {
        AnalysisResult result = parser.parse("id", "invalid-json");
        
        assertTrue(result instanceof AnalysisResult.CriticalFailure);
        assertEquals(ErrorCode.PARSING_ERROR, ((AnalysisResult.CriticalFailure) result).errorCode());
    }
}
