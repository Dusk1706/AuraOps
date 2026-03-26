package com.auraops.analyzer.application.usecases;

import com.auraops.analyzer.application.ports.in.AnalyzeIncidentUseCase;
import com.auraops.analyzer.application.ports.out.LLMProvider;
import com.auraops.analyzer.application.ports.out.TelemetrySource;
import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.Incident;
import com.auraops.analyzer.domain.service.IncidentAnalysisService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AnalyzeIncidentUseCaseImpl implements AnalyzeIncidentUseCase {

    private final LLMProvider llmProvider;
    private final TelemetrySource telemetrySource;
    private final IncidentAnalysisService incidentAnalysisService;
    private final VectorStore vectorStore;

    public AnalyzeIncidentUseCaseImpl(
        LLMProvider llmProvider,
        TelemetrySource telemetrySource,
        IncidentAnalysisService incidentAnalysisService,
        VectorStore vectorStore
    ) {
        this.llmProvider = Objects.requireNonNull(llmProvider, "llmProvider must not be null");
        this.telemetrySource = Objects.requireNonNull(telemetrySource, "telemetrySource must not be null");
        this.incidentAnalysisService = Objects.requireNonNull(
            incidentAnalysisService,
            "incidentAnalysisService must not be null"
        );
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
    }

    @Override
    public AnalysisResult execute(Incident incident) {
        Objects.requireNonNull(incident, "incident must not be null");
        Incident enrichedIncident = telemetrySource.enrich(incident);
        
        return incidentAnalysisService.assessReadiness(enrichedIncident)
            .<AnalysisResult>map(result -> result)
            .orElseGet(() -> {
                List<String> history = findSimilarIncidents(enrichedIncident);
                AnalysisResult result = llmProvider.analyze(enrichedIncident, history);
                
                if (result instanceof AnalysisResult.Success success) {
                    saveToHistory(enrichedIncident, success);
                }
                
                return result;
            });
    }

    private List<String> findSimilarIncidents(Incident incident) {
        String query = String.join(" ", incident.telemetry().logs());
        if (query.isBlank()) {
            query = "incident " + incident.resource().name() + " in namespace " + incident.resource().namespace();
        }

        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(3)
            .similarityThreshold(0.7)
            .build();
        try {
            return vectorStore.similaritySearch(request).stream()
                .map(Document::getText)
                .toList();
        } catch (Exception ex) {
            System.err.println("Vector Store search failed: " + ex.getMessage());
            return List.of();
        }
    }

    private void saveToHistory(Incident incident, AnalysisResult.Success success) {
        String content = String.format(
            "Incident in %s/%s diagnosed as: %s. Action taken: %s. Technical reasoning: %s",
            incident.resource().namespace(),
            incident.resource().name(),
            success.diagnosis(),
            success.recommendedAction().type(),
            success.technicalReasoning()
        );

        Document doc = new Document(content, Map.of(
            "incidentId", incident.incidentId(),
            "resource", incident.resource().name(),
            "namespace", incident.resource().namespace(),
            "action", success.recommendedAction().type()
        ));

        try {
            vectorStore.add(List.of(doc));
        } catch (Exception ex) {
            System.err.println("Vector Store add failed: " + ex.getMessage());
        }
    }
}
