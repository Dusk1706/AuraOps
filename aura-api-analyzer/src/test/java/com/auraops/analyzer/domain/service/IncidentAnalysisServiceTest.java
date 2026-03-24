package com.auraops.analyzer.domain.service;

import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.Incident;
import com.auraops.analyzer.domain.model.Metrics;
import com.auraops.analyzer.domain.model.Resource;
import com.auraops.analyzer.domain.model.Telemetry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentAnalysisServiceTest {

    private final IncidentAnalysisService service = new IncidentAnalysisService();

    @Test
    void assessReadiness_shouldReturnInconclusiveWhenMetricsAreMissing() {
        Incident incident = new Incident(
            "id-1",
            new Resource("Pod", "pod-1", "default"),
            new Telemetry(List.of("OutOfMemoryError"), new Metrics(null, null, 0), List.of())
        );

        AnalysisResult.Inconclusive result = service.assessReadiness(incident).orElseThrow();

        assertTrue(result.missingDataPoints().contains("metrics"));
    }

    @Test
    void assessReadiness_shouldAllowIncidentsWithLogsAndMetrics() {
        Incident incident = new Incident(
            "id-1",
            new Resource("Pod", "pod-1", "default"),
            new Telemetry(List.of("OutOfMemoryError"), new Metrics("1Gi", "500m", 1), List.of())
        );

        assertTrue(service.assessReadiness(incident).isEmpty());
    }
}
