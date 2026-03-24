package com.auraops.analyzer.infrastructure.adapters.out.telemetry;

import com.auraops.analyzer.application.ports.out.TelemetrySource;
import com.auraops.analyzer.domain.model.Incident;
import org.springframework.stereotype.Component;

@Component
public class PassThroughTelemetrySource implements TelemetrySource {

    @Override
    public Incident enrich(Incident incident) {
        return incident;
    }
}
