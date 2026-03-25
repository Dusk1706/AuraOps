package com.auraops.operator.infrastructure.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

@Component
public class HealerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(HealerEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public HealerEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate);
    }

    public void publish(
        String eventType,
        String policy,
        String action,
        String phase,
        String serviceName,
        String namespace,
        String details,
        String aiDiagnosis,
        Double aiConfidence
    ) {
        var payload = new HealerEventMessage(
            eventType,
            policy,
            action,
            severityFromPhase(phase),
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            serviceName,
            namespace,
            details,
            aiDiagnosis,
            aiConfidence
        );

        messagingTemplate.convertAndSend("/topic/healer-events", payload);
        log.debug("Published healer event {} for policy {}", eventType, policy);
    }

    private String severityFromPhase(String phase) {
        if (phase == null) {
            return "MEDIUM";
        }

        var normalized = phase.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HEALED" -> "LOW";
            case "POLICY_BLOCKED", "RATE_LIMITED", "ANALYSIS_BLOCKED" -> "HIGH";
            case "WAITING_FOR_TARGET" -> "MEDIUM";
            case "VERIFYING" -> "MEDIUM";
            default -> "MEDIUM";
        };
    }
}
