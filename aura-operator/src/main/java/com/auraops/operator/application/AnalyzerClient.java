package com.auraops.operator.application;

public interface AnalyzerClient {

    AnalyzerDecision analyze(IncidentContext incidentContext);
}
