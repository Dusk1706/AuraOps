package com.auraops.analyzer.application.ports.out;

import com.auraops.analyzer.domain.model.Incident;

public interface TelemetrySource {
    Incident enrich(Incident incident);
}
