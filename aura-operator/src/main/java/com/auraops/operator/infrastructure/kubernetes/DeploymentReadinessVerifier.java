package com.auraops.operator.infrastructure.kubernetes;

import com.auraops.operator.config.HealerProperties;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
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
            if (isReady(lastSeen)) {
                return VerificationResult.ready(lastSeen);
            }
            sleep(pollInterval);
        }

        return VerificationResult.failed(
            lastSeen == null
                ? "Deployment was never observed during verification"
                : "Deployment did not become ready within " + timeout
        );
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

        static VerificationResult ready(Deployment deployment) {
            int replicas = deployment.getSpec() == null || deployment.getSpec().getReplicas() == null
                ? 1
                : deployment.getSpec().getReplicas();
            return new VerificationResult(true, "Deployment passed readiness verification with " + replicas + " ready replicas");
        }

        static VerificationResult failed(String message) {
            return new VerificationResult(false, message);
        }
    }
}
