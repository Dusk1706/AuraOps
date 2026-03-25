package com.auraops.operator.infrastructure.realtime;

public record HealerEventMessage(
    String event_type,
    String policy,
    String action,
    String severity,
    String timestamp,
    String service_name,
    String namespace,
    String details,
    String ai_diagnosis,
    Double ai_confidence
) {
}
