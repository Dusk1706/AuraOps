package com.auraops.analyzer.application.usecases;

import com.auraops.analyzer.application.ports.in.AnalyzeIncidentUseCase;
import com.auraops.analyzer.application.ports.out.LLMProvider;
import com.auraops.analyzer.application.ports.out.TelemetrySource;
import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.Incident;
import com.auraops.analyzer.domain.service.IncidentAnalysisService;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AnalyzeIncidentUseCaseImpl implements AnalyzeIncidentUseCase {

    private final LLMProvider llmProvider;
    private final TelemetrySource telemetrySource;
    private final IncidentAnalysisService incidentAnalysisService;

    public AnalyzeIncidentUseCaseImpl(
        LLMProvider llmProvider,
        TelemetrySource telemetrySource,
        IncidentAnalysisService incidentAnalysisService
    ) {
        this.llmProvider = Objects.requireNonNull(llmProvider, "llmProvider must not be null");
        this.telemetrySource = Objects.requireNonNull(telemetrySource, "telemetrySource must not be null");
        this.incidentAnalysisService = Objects.requireNonNull(
            incidentAnalysisService,
            "incidentAnalysisService must not be null"
        );
    }

    @Override
    public AnalysisResult execute(Incident incident) {
        Objects.requireNonNull(incident, "incident must not be null");
        Incident enrichedIncident = telemetrySource.enrich(incident);
        return incidentAnalysisService.assessReadiness(enrichedIncident)
            .<AnalysisResult>map(result -> result)
            .orElseGet(() -> llmProvider.analyze(enrichedIncident));
    }
}
