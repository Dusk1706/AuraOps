package com.auraops.analyzer.application.ports.out;

import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.Incident;

public interface LLMProvider {
    AnalysisResult analyze(Incident incident);
}
