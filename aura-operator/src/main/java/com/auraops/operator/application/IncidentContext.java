package com.auraops.operator.application;

import java.util.List;
import java.util.Map;

public record IncidentContext(
    String incidentId,
    String namespace,
    String deploymentName,
    List<String> logs,
    List<String> traces,
    String memoryUsage,
    String cpuUsage,
    int restartCount,
    Map<String, Object> additionalMetrics
) {
}
