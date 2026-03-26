package com.auraops.analyzer.application.ports.out;

import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.Incident;
import java.util.List;

public interface LLMProvider {
    AnalysisResult analyze(Incident incident, List<String> historicalContext);
}
