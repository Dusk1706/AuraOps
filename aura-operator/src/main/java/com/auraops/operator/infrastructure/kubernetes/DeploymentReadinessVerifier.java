package com.auraops.operator.infrastructure.kubernetes;

import com.auraops.operator.config.HealerProperties;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class DeploymentReadinessVerifier {

    private final KubernetesClient kubernetesClient;
    private final HealerProperties healerProperties;

    public DeploymentReadinessVerifier(KubernetesClient kubernetesClient, HealerProperties healerProperties) {
        this.kubernetesClient = Objects.requireNonNull(kubernetesClient);
        this.healerProperties = Objects.requireNonNull(healerProperties);
    }

    public VerificationResult verify(String namespace, String deploymentName) {
        return verify(namespace, deploymentName, null);
    }

    public VerificationResult verify(String namespace, String deploymentName, Long minimumObservedGeneration) {
        Duration timeout = healerProperties.getVerificationTimeout();
        Duration pollInterval = healerProperties.getVerificationPollInterval();
        Instant deadline = Instant.now().plus(timeout);

        Deployment lastSeen = null;
        while (!Instant.now().isAfter(deadline)) {
            lastSeen = kubernetesClient.apps().deployments()
                .inNamespace(namespace)
                .withName(deploymentName)
                .get();
            if (lastSeen == null) {
                return VerificationResult.failed("Deployment disappeared during verification");
            }

            long expectedGeneration = expectedObservedGeneration(lastSeen, minimumObservedGeneration);
            if (isReady(lastSeen)
                && observedGenerationSatisfied(lastSeen, expectedGeneration)
                && activeReplicaSetReady(lastSeen, expectedGeneration)) {
                return VerificationResult.ready(lastSeen, expectedGeneration);
            }
            sleep(pollInterval);
        }

        return VerificationResult.failed(
            lastSeen == null
                ? "Deployment was never observed during verification"
                : "Deployment did not become ready within " + timeout
                + " (observedGeneration=" + observedGeneration(lastSeen)
                + ", expectedGeneration=" + expectedObservedGeneration(lastSeen, minimumObservedGeneration) + ")"
        );
    }

    private long expectedObservedGeneration(Deployment deployment, Long minimumObservedGeneration) {
        long deploymentGeneration = deployment.getMetadata() == null || deployment.getMetadata().getGeneration() == null
            ? 1L
            : deployment.getMetadata().getGeneration();
        if (minimumObservedGeneration == null) {
            return deploymentGeneration;
        }
        return Math.max(minimumObservedGeneration, deploymentGeneration);
    }

    private boolean isReady(Deployment deployment) {
        if (deployment.getSpec() == null || deployment.getStatus() == null) {
            return false;
        }

        int desiredReplicas = defaultReplicas(deployment.getSpec().getReplicas());
        int readyReplicas = defaultReplicas(deployment.getStatus().getReadyReplicas());
        int availableReplicas = defaultReplicas(deployment.getStatus().getAvailableReplicas());
        return readyReplicas >= desiredReplicas
            && availableReplicas >= desiredReplicas;
    }

    private boolean observedGenerationSatisfied(Deployment deployment, long expectedGeneration) {
        return observedGeneration(deployment) >= expectedGeneration;
    }

    private long observedGeneration(Deployment deployment) {
        if (deployment.getStatus() == null || deployment.getStatus().getObservedGeneration() == null) {
            return 0L;
        }
        return deployment.getStatus().getObservedGeneration();
    }

    private boolean activeReplicaSetReady(Deployment deployment, long expectedGeneration) {
        if (deployment.getSpec() == null
            || deployment.getSpec().getSelector() == null
            || deployment.getSpec().getSelector().getMatchLabels() == null
            || deployment.getSpec().getSelector().getMatchLabels().isEmpty()) {
            return true;
        }
        if (deployment.getMetadata() == null || deployment.getMetadata().getUid() == null) {
            return true;
        }

        String namespace = deployment.getMetadata().getNamespace();
        Map<String, String> selector = deployment.getSpec().getSelector().getMatchLabels();
        String deploymentUid = deployment.getMetadata().getUid();
        int desiredReplicas = defaultReplicas(deployment.getSpec().getReplicas());

        var replicaSetList = kubernetesClient.apps().replicaSets()
            .inNamespace(namespace)
            .withLabels(selector)
            .list();
        List<ReplicaSet> replicaSets = replicaSetList == null || replicaSetList.getItems() == null
            ? List.of()
            : replicaSetList.getItems();

        if (replicaSets.isEmpty()) {
            return true;
        }

        return replicaSets.stream()
            .filter(this::hasStatus)
            .filter(replicaSet -> isOwnedByDeployment(replicaSet, deploymentUid))
            .filter(replicaSet -> replicaSet.getStatus().getObservedGeneration() != null
                && replicaSet.getMetadata() != null
                && replicaSet.getMetadata().getGeneration() != null
                && replicaSet.getStatus().getObservedGeneration() >= replicaSet.getMetadata().getGeneration())
            .max(Comparator.comparingLong(this::replicaSetRevision))
            .map(replicaSet -> {
                long readyReplicas = defaultReplicas(replicaSet.getStatus().getReadyReplicas());
                long deploymentObserved = observedGeneration(deployment);
                return deploymentObserved >= expectedGeneration && readyReplicas >= desiredReplicas;
            })
            .orElse(false);
    }

    private boolean hasStatus(ReplicaSet replicaSet) {
        return replicaSet != null && replicaSet.getStatus() != null && replicaSet.getMetadata() != null;
    }

    private boolean isOwnedByDeployment(ReplicaSet replicaSet, String deploymentUid) {
        if (replicaSet.getMetadata() == null || replicaSet.getMetadata().getOwnerReferences() == null) {
            return false;
        }
        return replicaSet.getMetadata().getOwnerReferences().stream()
            .anyMatch(owner -> isDeploymentOwner(owner, deploymentUid));
    }

    private boolean isDeploymentOwner(OwnerReference ownerReference, String deploymentUid) {
        return ownerReference != null
            && "Deployment".equals(ownerReference.getKind())
            && deploymentUid.equals(ownerReference.getUid());
    }

    private long replicaSetRevision(ReplicaSet replicaSet) {
        if (replicaSet.getMetadata() == null || replicaSet.getMetadata().getAnnotations() == null) {
            return -1L;
        }
        String revision = replicaSet.getMetadata().getAnnotations().get("deployment.kubernetes.io/revision");
        if (revision == null) {
            return -1L;
        }
        try {
            return Long.parseLong(revision);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    private int defaultReplicas(Integer replicas) {
        return replicas == null ? 1 : replicas;
    }

    private void sleep(Duration pollInterval) {
        try {
            Thread.sleep(Math.max(1L, pollInterval.toMillis()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Deployment verification was interrupted", ex);
        }
    }

    public record VerificationResult(boolean ready, String message) {

        static VerificationResult ready(Deployment deployment, long expectedGeneration) {
            int replicas = deployment.getSpec() == null || deployment.getSpec().getReplicas() == null
                ? 1
                : deployment.getSpec().getReplicas();
            return new VerificationResult(
                true,
                "Deployment passed readiness verification with " + replicas
                    + " ready replicas and observedGeneration >= " + expectedGeneration
            );
        }

        static VerificationResult failed(String message) {
            return new VerificationResult(false, message);
        }
    }
}
