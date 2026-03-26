package com.auraops.operator.application;

import com.auraops.operator.infrastructure.realtime.HealerEventMessage;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class DashboardService {

    private final KubernetesClient kubernetesClient;
    private final ConcurrentLinkedDeque<HealerEventMessage> eventBuffer = new ConcurrentLinkedDeque<>();
    private static final int MAX_BUFFER_SIZE = 100;

    public DashboardService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public void addEvent(HealerEventMessage event) {
        eventBuffer.addFirst(event);
        while (eventBuffer.size() > MAX_BUFFER_SIZE) {
            eventBuffer.removeLast();
        }
    }

    public List<HealerEventMessage> getRecentEvents() {
        return new ArrayList<>(eventBuffer);
    }

    public List<ClusterNodeDto> getClusterNodes() {
        try {
            return kubernetesClient.apps().deployments().inAnyNamespace().list().getItems().stream()
                .filter(d -> d.getMetadata().getLabels() != null && d.getMetadata().getLabels().containsKey("auraops.managed"))
                .map(this::mapToDto)
                .toList();
        } catch (Exception e) {
            // Fallback for development if K8s is not available or other errors
            return Collections.emptyList();
        }
    }

    private ClusterNodeDto mapToDto(Deployment deployment) {
        String serviceName = deployment.getMetadata().getName();
        String namespace = deployment.getMetadata().getNamespace();
        
        Integer replicas = deployment.getSpec().getReplicas();
        Integer readyReplicas = deployment.getStatus() != null ? deployment.getStatus().getReadyReplicas() : 0;
        
        String health = "UNKNOWN";
        if (readyReplicas != null && replicas != null) {
            if (readyReplicas.equals(replicas)) {
                health = "HEALTHY";
            } else if (readyReplicas > 0) {
                health = "DEGRADED";
            } else {
                health = "CRITICAL";
            }
        }

        return new ClusterNodeDto(
            serviceName,
            namespace,
            health,
            "STABLE",
            replicas != null ? replicas : 0,
            readyReplicas != null ? readyReplicas : 0,
            "NONE",
            OffsetDateTime.now().toString(),
            null,
            1.0
        );
    }

    public record ClusterNodeDto(
        String serviceName,
        String namespace,
        String health,
        String healingState,
        int replicas,
        int healthyReplicas,
        String lastAction,
        String lastEventAt,
        String aiDiagnosis,
        Double aiConfidence
    ) {}
}
