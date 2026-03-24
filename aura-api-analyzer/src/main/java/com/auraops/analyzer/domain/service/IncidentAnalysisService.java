package com.auraops.analyzer.domain.service;

import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.Incident;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class IncidentAnalysisService {

    public Optional<AnalysisResult.Inconclusive> assessReadiness(Incident incident) {
        List<String> missingDataPoints = new ArrayList<>();

        if (incident.telemetry().logs().isEmpty()) {
            missingDataPoints.add("logs");
        }

        if (incident.telemetry().metrics().memoryUsage() == null
                && incident.telemetry().metrics().cpuUsage() == null
                && incident.telemetry().metrics().restartCount() == 0
                && incident.telemetry().metrics().additionalMetrics().isEmpty()) {
            missingDataPoints.add("metrics");
        }

        if (missingDataPoints.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new AnalysisResult.Inconclusive(
                incident.incidentId(),
                "Incident telemetry is insufficient for deterministic analysis",
                missingDataPoints
        ));
    }
}
