package com.auraops.operator.application;

import com.auraops.operator.config.HealerProperties;
import com.auraops.operator.domain.HealingActionType;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class HealingSafetyService {

    private final HealerProperties healerProperties;

    public HealingSafetyService(HealerProperties healerProperties) {
        this.healerProperties = Objects.requireNonNull(healerProperties);
    }

    public PolicyDecision validate(ActionPlan plan, Deployment deployment) {
        if (plan.actionType() == HealingActionType.SCALE_OUT) {
            return new PolicyDecision.Execute(plan);
        }
        if (deployment.getSpec() == null || deployment.getSpec().getStrategy() == null) {
            return new PolicyDecision.Execute(plan);
        }
        String strategyType = deployment.getSpec().getStrategy().getType();
        if ("Recreate".equalsIgnoreCase(strategyType)) {
            return new PolicyDecision.Reject("Recreate strategy would impact more than the allowed 25% of pods");
        }

        int replicas = Math.max(1, plan.currentReplicas());
        int allowedImpact = Math.max(1, (int) Math.ceil(replicas * healerProperties.getMaxImpactRatio()));
        IntOrString maxUnavailable = deployment.getSpec().getStrategy().getRollingUpdate() == null
            ? null
            : deployment.getSpec().getStrategy().getRollingUpdate().getMaxUnavailable();
        int configuredImpact = resolveMaxUnavailable(maxUnavailable, replicas);
        if (configuredImpact > allowedImpact) {
            return new PolicyDecision.Reject(
                "Deployment rolling update allows %d unavailable pods, exceeding the policy max of %d"
                    .formatted(configuredImpact, allowedImpact)
            );
        }
        return new PolicyDecision.Execute(plan);
    }

    private int resolveMaxUnavailable(IntOrString maxUnavailable, int replicas) {
        if (maxUnavailable == null) {
            return Math.max(1, (int) Math.ceil(replicas * 0.25d));
        }
        if (maxUnavailable.getIntVal() != null) {
            return maxUnavailable.getIntVal();
        }
        String value = maxUnavailable.getStrVal();
        if (value != null && value.endsWith("%")) {
            int percentage = Integer.parseInt(value.substring(0, value.length() - 1));
            return Math.max(1, (int) Math.ceil(replicas * (percentage / 100.0d)));
        }
        return Math.max(1, Integer.parseInt(value));
    }
}
