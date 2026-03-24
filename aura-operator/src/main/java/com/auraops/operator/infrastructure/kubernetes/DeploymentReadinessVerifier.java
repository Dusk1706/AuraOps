package com.auraops.operator.infrastructure.kubernetes;

import com.auraops.operator.config.HealerProperties;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class DeploymentReadinessVerifier {

    private final KubernetesClient kubernetesClient;

    public DeploymentReadinessVerifier(KubernetesClient kubernetesClient) {
        this.kubernetesClient = Objects.requireNonNull(kubernetesClient);
    }

    /**
     * Performs an atomic (non-blocking) verification of deployment readiness.
     * Checks if observedGeneration matches or exceeds expectedGeneration and if pods are ready.
     */
    public VerificationResult verify(Deployment deployment, Long expectedGeneration) {
        if (deployment == null) {
            return VerificationResult.failed("Deployment not found");
        }

        long targetGeneration = expectedGeneration != null ? expectedGeneration : observedGeneration(deployment);
        
        if (!observedGenerationSatisfied(deployment, targetGeneration)) {
            return VerificationResult.failed("Waiting for Kubernetes to observe the new generation (observed=" 
                + observedGeneration(deployment) + ", expected=" + targetGeneration + ")");
        }

        if (!isReady(deployment)) {
            return VerificationResult.failed("Deployment replicas are not yet ready (desired=" 
                + defaultReplicas(deployment.getSpec().getReplicas()) + ")");
        }

        if (!activeReplicaSetReady(deployment, targetGeneration)) {
            return VerificationResult.failed("Active ReplicaSet for generation " + targetGeneration + " is not yet ready");
        }

        return VerificationResult.ready(deployment, targetGeneration);
    }

    private boolean isReady(Deployment deployment) {
        if (deployment.getSpec() == null || deployment.getStatus() == null) {
            return false;
        }

        int desiredReplicas = defaultReplicas(deployment.getSpec().getReplicas());
        int readyReplicas = defaultReplicas(deployment.getStatus().getReadyReplicas());
        int availableReplicas = defaultReplicas(deployment.getStatus().getAvailableReplicas());
        
        return readyReplicas >= desiredReplicas && availableReplicas >= desiredReplicas;
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
            // Only consider the RS that matches the deployment's current state (via revision)
            .max(Comparator.comparingLong(this::replicaSetRevision))
            .map(replicaSet -> {
                boolean rsObserved = replicaSet.getStatus().getObservedGeneration() != null
                    && replicaSet.getMetadata().getGeneration() != null
                    && replicaSet.getStatus().getObservedGeneration() >= replicaSet.getMetadata().getGeneration();
                
                int rsReadyReplicas = defaultReplicas(replicaSet.getStatus().getReadyReplicas());
                
                return rsObserved && rsReadyReplicas >= desiredReplicas;
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

    public record VerificationResult(boolean ready, String message) {
        public static VerificationResult ready(Deployment deployment, long expectedGeneration) {
            int replicas = deployment.getSpec() == null || deployment.getSpec().getReplicas() == null
                ? 1
                : deployment.getSpec().getReplicas();
            return new VerificationResult(
                true,
                "Deployment passed readiness verification for generation " + expectedGeneration 
                    + " with " + replicas + " ready replicas"
            );
        }

        public static VerificationResult failed(String message) {
            return new VerificationResult(false, message);
        }
    }
}
