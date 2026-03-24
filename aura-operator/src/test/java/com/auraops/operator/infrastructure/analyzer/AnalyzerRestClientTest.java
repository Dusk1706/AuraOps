package com.auraops.operator.infrastructure.analyzer;

import com.auraops.operator.application.AnalyzerDecision;
import com.auraops.operator.application.IncidentContext;
import com.auraops.operator.config.AnalyzerClientProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AnalyzerRestClientTest {

    private MockRestServiceServer server;
    private AnalyzerRestClient client;

    @BeforeEach
    void setUp() {
        AnalyzerClientProperties properties = new AnalyzerClientProperties();
        properties.setBaseUrl("http://localhost:8080");
        properties.setAnalyzePath("/api/v1/analyze");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AnalyzerRestClient(
            builder.baseUrl(properties.getBaseUrl()).build(),
            properties,
            CircuitBreakerRegistry.ofDefaults(),
            new ObjectMapper()
        );
    }

    @Test
    void analyze_mapsSuccessResponse() {
        server.expect(requestTo("http://localhost:8080/api/v1/analyze"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "diagnosis": "Heap growth detected",
                      "confidence": 0.98,
                      "recommended_action": {
                        "type": "ROLLING_RESTART",
                        "parameters": {
                          "namespace": "payments"
                        }
                      },
                      "explanation": "Deterministic restart"
                    }
                    """));

        AnalyzerDecision result = client.analyze(sampleContext());

        assertThat(result).isInstanceOf(AnalyzerDecision.Success.class);
        assertThat(((AnalyzerDecision.Success) result).actionType()).isEqualTo("ROLLING_RESTART");
    }

    @Test
    void analyze_mapsProblemDetailAsInconclusive() {
        server.expect(requestTo("http://localhost:8080/api/v1/analyze"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("""
                    {
                      "type": "https://auraops.dev/problems/ai_low_confidence",
                      "title": "Analysis Inconclusive",
                      "status": 422,
                      "detail": "Low data",
                      "error_code": "AI_LOW_CONFIDENCE",
                      "message": "Low data",
                      "requires_human": true
                    }
                    """));

        AnalyzerDecision result = client.analyze(sampleContext());

        assertThat(result).isInstanceOf(AnalyzerDecision.Inconclusive.class);
        assertThat(((AnalyzerDecision.Inconclusive) result).errorCode()).isEqualTo("AI_LOW_CONFIDENCE");
    }

    private IncidentContext sampleContext() {
        return new IncidentContext(
            "payments/payments-api/1",
            "payments",
            "payments-api",
            List.of("oom"),
            List.of(),
            "unknown",
            "unknown",
            4,
            Map.of("readyReplicas", 1)
        );
    }
}
