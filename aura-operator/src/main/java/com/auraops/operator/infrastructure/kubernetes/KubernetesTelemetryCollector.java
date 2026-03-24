package com.auraops.operator.infrastructure.kubernetes;

import com.auraops.operator.application.IncidentContext;
import com.auraops.operator.config.HealerProperties;
import com.auraops.operator.infrastructure.observability.LokiLogCollector;
import com.auraops.operator.infrastructure.observability.TempoTraceCollector;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class KubernetesTelemetryCollector {

    private final KubernetesClient kubernetesClient;
    private final HealerProperties healerProperties;
    private final LokiLogCollector lokiLogCollector;
    private final TempoTraceCollector tempoTraceCollector;

    public KubernetesTelemetryCollector(
        KubernetesClient kubernetesClient,
        HealerProperties healerProperties,
        LokiLogCollector lokiLogCollector,
        TempoTraceCollector tempoTraceCollector
    ) {
        this.kubernetesClient = Objects.requireNonNull(kubernetesClient);
        this.healerProperties = Objects.requireNonNull(healerProperties);
        this.lokiLogCollector = Objects.requireNonNull(lokiLogCollector);
        this.tempoTraceCollector = Objects.requireNonNull(tempoTraceCollector);
    }

    public IncidentContext collect(Deployment deployment) {
        String namespace = deployment.getMetadata().getNamespace();
        String deploymentName = deployment.getMetadata().getName();
        List<Pod> pods = loadTargetPods(deployment);
        List<String> logs = collectLogs(deployment, pods);
        List<String> traces = tempoTraceCollector.collect(deployment);
        int restartCount = pods.stream()
            .flatMap(pod -> safeContainerStatuses(pod).stream())
            .mapToInt(ContainerStatus::getRestartCount)
            .sum();

        Map<String, Object> additionalMetrics = new LinkedHashMap<>();
        additionalMetrics.put("readyReplicas", defaulted(deployment.getStatus() == null ? null : deployment.getStatus().getReadyReplicas()));
        additionalMetrics.put("availableReplicas", defaulted(deployment.getStatus() == null ? null : deployment.getStatus().getAvailableReplicas()));
        additionalMetrics.put("updatedReplicas", defaulted(deployment.getStatus() == null ? null : deployment.getStatus().getUpdatedReplicas()));
        additionalMetrics.put("observedGeneration", deployment.getStatus() == null ? null : deployment.getStatus().getObservedGeneration());
        additionalMetrics.put("sampledAt", OffsetDateTime.now().toString());

        return new IncidentContext(
            namespace + "/" + deploymentName + "/" + OffsetDateTime.now().toEpochSecond(),
            namespace,
            deploymentName,
            logs,
            traces,
            "unknown",
            "unknown",
            restartCount,
            additionalMetrics
        );
    }

    private List<Pod> loadTargetPods(Deployment deployment) {
        Map<String, String> labels = deployment.getSpec().getSelector().getMatchLabels();
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        PodList podList = kubernetesClient.pods()
            .inNamespace(deployment.getMetadata().getNamespace())
            .withLabels(labels)
            .list();
        return podList == null || podList.getItems() == null ? List.of() : podList.getItems();
    }

    private List<String> collectLogs(Deployment deployment, List<Pod> pods) {
        List<String> externalLogs = lokiLogCollector.collect(deployment, healerProperties.getMaxTotalLogLines());
        if (!externalLogs.isEmpty()) {
            return externalLogs;
        }

        List<String> collected = new ArrayList<>();
        pods.stream()
            .sorted(Comparator.comparing(pod -> pod.getMetadata().getName()))
            .forEach(pod -> {
                if (collected.size() >= healerProperties.getMaxTotalLogLines()) {
                    return;
                }
                String summary = summarizePodState(pod);
                if (!summary.isBlank()) {
                    collected.add(summary);
                }
                try {
                    String rawLog = kubernetesClient.pods()
                        .inNamespace(pod.getMetadata().getNamespace())
                        .withName(pod.getMetadata().getName())
                        .tailingLines(healerProperties.getLogLinesPerPod())
                        .getLog();
                    if (rawLog != null && !rawLog.isBlank()) {
                        for (String line : rawLog.lines().toList()) {
                            if (line.isBlank()) {
                                continue;
                            }
                            collected.add("[" + pod.getMetadata().getName() + "] " + line);
                            if (collected.size() >= healerProperties.getMaxTotalLogLines()) {
                                break;
                            }
                        }
                    }
                } catch (Exception ex) {
                    collected.add("[" + pod.getMetadata().getName() + "] log collection failed: " + ex.getClass().getSimpleName());
                }
            });

        if (collected.isEmpty()) {
            return List.of("No pod logs were available; using deployment and pod state only.");
        }
        return collected;
    }

    private String summarizePodState(Pod pod) {
        List<String> parts = new ArrayList<>();
        parts.add("[" + pod.getMetadata().getName() + "] phase=" + defaultString(pod.getStatus() == null ? null : pod.getStatus().getPhase()));
        for (ContainerStatus status : safeContainerStatuses(pod)) {
            if (status.getState() != null && status.getState().getWaiting() != null) {
                parts.add(status.getName() + " waiting=" + defaultString(status.getState().getWaiting().getReason()));
            }
            if (status.getState() != null && status.getState().getTerminated() != null) {
                parts.add(status.getName() + " terminated=" + defaultString(status.getState().getTerminated().getReason()));
            }
            if (status.getRestartCount() > 0) {
                parts.add(status.getName() + " restarts=" + status.getRestartCount());
            }
        }
        return String.join(" ", parts);
    }

    private List<ContainerStatus> safeContainerStatuses(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return List.of();
        }
        return pod.getStatus().getContainerStatuses();
    }

    private Integer defaulted(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultString(String value) {
        return value == null ? "unknown" : value;
    }
}
