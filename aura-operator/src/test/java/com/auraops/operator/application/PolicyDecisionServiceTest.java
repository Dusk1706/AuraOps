package com.auraops.operator.application;

import com.auraops.operator.crd.HealerPolicy;
import com.auraops.operator.crd.HealerPolicySpec;
import com.auraops.operator.crd.HealingStrategySpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDecisionServiceTest {

    private final PolicyDecisionService service = new PolicyDecisionService();

    @Test
    void evaluate_acceptsRestartAllowedByPolicy() {
        HealerPolicy policy = policy("payments-api", 0.95, strategy("MemoryLeak", "ROLLING_RESTART", null));
        Deployment deployment = deployment("payments-api", 4);
        AnalyzerDecision.Success success = new AnalyzerDecision.Success(
            "Heap growth detected",
            0.98,
            "ROLLING_RESTART",
            Map.of(),
            "Restart is deterministic"
        );

        PolicyDecision result = service.evaluate(policy, deployment, success);

        assertThat(result).isInstanceOf(PolicyDecision.Execute.class);
        ActionPlan plan = ((PolicyDecision.Execute) result).actionPlan();
        assertThat(plan.actionType().name()).isEqualTo("ROLLING_RESTART");
        assertThat(plan.currentReplicas()).isEqualTo(4);
        assertThat(plan.targetReplicas()).isEqualTo(4);
    }

    @Test
    void evaluate_rejectsWhenConfidenceBelowThreshold() {
        HealerPolicy policy = policy("payments-api", 0.99, strategy("MemoryLeak", "ROLLING_RESTART", null));
        Deployment deployment = deployment("payments-api", 4);
        AnalyzerDecision.Success success = new AnalyzerDecision.Success(
            "Heap growth detected",
            0.95,
            "ROLLING_RESTART",
            Map.of(),
            "Restart is deterministic"
        );

        PolicyDecision result = service.evaluate(policy, deployment, success);

        assertThat(result).isInstanceOf(PolicyDecision.Reject.class);
        assertThat(((PolicyDecision.Reject) result).reason()).contains("below policy threshold");
    }

    @Test
    void evaluate_capsScaleOutAtPolicyMaximum() {
        HealerPolicy policy = policy("payments-api", 0.95, strategy("LatencySpike", "SCALE_OUT", 5));
        Deployment deployment = deployment("payments-api", 5);
        AnalyzerDecision.Success success = new AnalyzerDecision.Success(
            "Sustained latency spike",
            0.99,
            "SCALE_OUT",
            Map.of(),
            "Scale out is deterministic"
        );

        PolicyDecision result = service.evaluate(policy, deployment, success);

        assertThat(result).isInstanceOf(PolicyDecision.Execute.class);
        assertThat(((PolicyDecision.Execute) result).actionPlan().targetReplicas()).isEqualTo(5);
    }

    private HealerPolicy policy(String deployment, double threshold, HealingStrategySpec strategy) {
        HealerPolicy policy = new HealerPolicy();
        policy.setMetadata(new ObjectMetaBuilder().withName("payments-auto-heal").withNamespace("payments").build());
        HealerPolicySpec spec = new HealerPolicySpec();
        spec.setTargetDeployment(deployment);
        spec.setAiConfidenceThreshold(threshold);
        spec.setStrategies(List.of(strategy));
        policy.setSpec(spec);
        return policy;
    }

    private HealingStrategySpec strategy(String type, String action, Integer maxReplicas) {
        HealingStrategySpec spec = new HealingStrategySpec();
        spec.setType(type);
        spec.setAction(action);
        spec.setMaxReplicas(maxReplicas);
        return spec;
    }

    private Deployment deployment(String name, int replicas) {
        Deployment deployment = new Deployment();
        deployment.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace("payments").build());
        deployment.setSpec(new DeploymentSpecBuilder().withReplicas(replicas).build());
        return deployment;
    }
}
