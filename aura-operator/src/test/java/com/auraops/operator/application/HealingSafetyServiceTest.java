package com.auraops.operator.application;

import com.auraops.operator.config.HealerProperties;
import com.auraops.operator.domain.HealingActionType;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategyBuilder;
import io.fabric8.kubernetes.api.model.apps.RollingUpdateDeploymentBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealingSafetyServiceTest {

    private final HealerProperties properties = new HealerProperties();
    private final HealingSafetyService service = new HealingSafetyService(properties);

    @Test
    void validate_rejectsRecreateStrategy() {
        PolicyDecision result = service.validate(plan(4, 4, HealingActionType.ROLLING_RESTART), deployment("Recreate", null));

        assertThat(result).isInstanceOf(PolicyDecision.Reject.class);
    }

    @Test
    void validate_rejectsIfMaxUnavailableExceedsQuarterOfPods() {
        PolicyDecision result = service.validate(
            plan(8, 8, HealingActionType.ROLLING_RESTART),
            deployment("RollingUpdate", new IntOrString("50%"))
        );

        assertThat(result).isInstanceOf(PolicyDecision.Reject.class);
        assertThat(((PolicyDecision.Reject) result).reason()).contains("exceeding the policy max");
    }

    @Test
    void validate_acceptsConservativeRollingUpdate() {
        PolicyDecision result = service.validate(
            plan(8, 8, HealingActionType.ROLLING_RESTART),
            deployment("RollingUpdate", new IntOrString("25%"))
        );

        assertThat(result).isInstanceOf(PolicyDecision.Execute.class);
    }

    private ActionPlan plan(int currentReplicas, int targetReplicas, HealingActionType type) {
        return new ActionPlan(type, currentReplicas, targetReplicas, false, "diag", 0.99, "explanation");
    }

    private Deployment deployment(String strategyType, IntOrString maxUnavailable) {
        Deployment deployment = new Deployment();
        deployment.setMetadata(new ObjectMetaBuilder().withName("payments-api").withNamespace("payments").build());
        deployment.setSpec(new DeploymentSpecBuilder()
            .withReplicas(8)
            .withStrategy(new DeploymentStrategyBuilder()
                .withType(strategyType)
                .withRollingUpdate(maxUnavailable == null ? null : new RollingUpdateDeploymentBuilder().withMaxUnavailable(maxUnavailable).build())
                .build())
            .build());
        return deployment;
    }
}
