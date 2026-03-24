package com.auraops.analyzer.application.ports.in;

import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.Incident;

public interface AnalyzeIncidentUseCase {
    AnalysisResult execute(Incident incident);
}
