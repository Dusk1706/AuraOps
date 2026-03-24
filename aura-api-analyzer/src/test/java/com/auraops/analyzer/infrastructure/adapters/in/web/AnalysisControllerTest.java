package com.auraops.analyzer.infrastructure.adapters.in.web;

import com.auraops.analyzer.application.ports.in.AnalyzeIncidentUseCase;
import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.RemediationAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyzeIncidentUseCase analyzeIncidentUseCase;

    @Test
    void analyze_success_mapsToExplanation() throws Exception {
        AnalysisResult.Success success = new AnalysisResult.Success(
            "id-123", "diagnosis", 0.9,
            new RemediationAction("RESTART", Map.of()), "technical reason"
        );

        when(analyzeIncidentUseCase.execute(any())).thenReturn(success);

        String json = """
            {
              "incident_id": "id-123",
              "resource": { "kind": "Pod", "name": "pod-1", "namespace": "default" },
              "telemetry": {
                "logs": ["error log"],
                "metrics": { "memory_usage": "1Gi", "cpu_usage": "0.5", "restart_count": 0 }
              }
            }
            """;

        mockMvc.perform(post("/api/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("technical reason"))
                .andExpect(jsonPath("$.diagnosis").value("diagnosis"));
    }

    @Test
    void analyze_invalidPayload_returnsProblemDetailWithCorporateProperties() throws Exception {
        String invalidJson = "{ \"incident_id\": \"\" }";

        mockMvc.perform(post("/api/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid Payload"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error_code").value("INVALID_PAYLOAD"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.requires_human").value(false));
    }

    @Test
    void analyze_inconclusive_returnsProblemDetail() throws Exception {
        AnalysisResult.Inconclusive inconclusive = new AnalysisResult.Inconclusive("id", "Low data", List.of("traces"));

        when(analyzeIncidentUseCase.execute(any())).thenReturn(inconclusive);

        String json = """
            {
              "incident_id": "id-123",
              "resource": { "kind": "Pod", "name": "pod-1", "namespace": "default" },
              "telemetry": {
                "logs": ["error log"],
                "metrics": { "memory_usage": "1Gi", "cpu_usage": "0.5", "restart_count": 0 }
              }
            }
            """;

        mockMvc.perform(post("/api/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Analysis Inconclusive"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error_code").value("AI_LOW_CONFIDENCE"))
                .andExpect(jsonPath("$.requires_human").value(true));
    }

    @Test
    void analyze_malformedPayload_returnsProblemDetailWithCorporateProperties() throws Exception {
        String invalidJson = """
            {
              "incident_id": "id-123",
              "resource": { "kind": "Pod", "name": "pod-1", "namespace": "default" },
              "telemetry": {
                "logs": ["error log"],
                "metrics": { "memory_usage": "1Gi", "cpu_usage": "0.5", "restart_count": "oops" }
              }
            }
            """;

        mockMvc.perform(post("/api/v1/analyze")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid Payload"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error_code").value("INVALID_PAYLOAD"));
    }
}
