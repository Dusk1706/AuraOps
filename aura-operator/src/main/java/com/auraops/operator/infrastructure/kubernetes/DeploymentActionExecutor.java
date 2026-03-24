package com.auraops.operator.infrastructure.kubernetes;

import com.auraops.operator.application.ActionPlan;
import com.auraops.operator.domain.HealingActionType;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class DeploymentActionExecutor {

    private final KubernetesClient kubernetesClient;

    public DeploymentActionExecutor(KubernetesClient kubernetesClient) {
        this.kubernetesClient = Objects.requireNonNull(kubernetesClient);
    }

    public void execute(Deployment deployment, ActionPlan plan) {
        if (plan.actionType() == HealingActionType.SCALE_OUT) {
            scale(deployment, plan.targetReplicas());
            return;
        }
        rollingRestart(deployment, plan.requestHeapDump());
    }

    private void scale(Deployment deployment, int targetReplicas) {
        kubernetesClient.apps().deployments()
            .inNamespace(deployment.getMetadata().getNamespace())
            .withName(deployment.getMetadata().getName())
            .scale(targetReplicas);
    }

    private void rollingRestart(Deployment deployment, boolean requestHeapDump) {
        kubernetesClient.apps().deployments()
            .inNamespace(deployment.getMetadata().getNamespace())
            .withName(deployment.getMetadata().getName())
            .edit(current -> {
                if (current.getSpec().getTemplate().getMetadata().getAnnotations() == null) {
                    current.getSpec().getTemplate().getMetadata().setAnnotations(new LinkedHashMap<>());
                }
                Map<String, String> annotations = current.getSpec().getTemplate().getMetadata().getAnnotations();
                annotations.put("auraops.io/restartedAt", OffsetDateTime.now().toString());
                if (requestHeapDump) {
                    annotations.put("auraops.io/heapDumpRequested", "true");
                }
                return current;
            });
    }
}
