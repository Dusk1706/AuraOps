package com.auraops.analyzer.infrastructure.config;

import com.auraops.analyzer.application.ports.out.LLMProvider;
import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.Incident;
import com.auraops.analyzer.domain.model.RemediationAction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@Configuration
@Profile("e2e")
public class E2eAnalyzerConfiguration {

    @Bean
    LLMProvider e2eLlmProvider() {
        return this::analyzeDeterministically;
    }

    private AnalysisResult analyzeDeterministically(Incident incident) {
        if ("payments-api".equals(incident.resource().name())) {
            return new AnalysisResult.Success(
                incident.incidentId(),
                "Deterministic e2e diagnosis: sustained latency saturation on payments-api",
                0.99,
                new RemediationAction("SCALE_OUT", Map.of()),
                "The e2e analyzer profile returns SCALE_OUT for payments-api so the operator can be validated end-to-end."
            );
        }

        if (incident.telemetry().metrics().restartCount() > 0) {
            return new AnalysisResult.Success(
                incident.incidentId(),
                "Deterministic e2e diagnosis: repeated restarts indicate a restart-based mitigation path",
                0.98,
                new RemediationAction("ROLLING_RESTART", Map.of("capture_heap_dump", true)),
                "The e2e analyzer profile returns a restart action when restart_count is greater than zero."
            );
        }

        return new AnalysisResult.Inconclusive(
            incident.incidentId(),
            "The e2e analyzer profile requires payments-api traffic saturation or restart evidence",
            java.util.List.of("restart_count")
        );
    }
}
