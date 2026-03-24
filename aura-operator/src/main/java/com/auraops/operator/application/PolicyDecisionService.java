package com.auraops.operator.application;

import com.auraops.operator.crd.HealerPolicy;
import com.auraops.operator.crd.HealingStrategySpec;
import com.auraops.operator.domain.HealingActionType;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class PolicyDecisionService {

    public PolicyDecision evaluate(HealerPolicy policy, Deployment deployment, AnalyzerDecision.Success decision) {
        Objects.requireNonNull(policy);
        Objects.requireNonNull(deployment);
        Objects.requireNonNull(decision);

        if (decision.confidence() < policy.getSpec().getAiConfidenceThreshold()) {
            return new PolicyDecision.Reject(
                "Analyzer confidence %.2f is below policy threshold %.2f".formatted(
                    decision.confidence(),
                    policy.getSpec().getAiConfidenceThreshold()
                )
            );
        }

        HealingActionType actionType;
        try {
            actionType = HealingActionType.fromAnalyzerValue(decision.actionType());
        } catch (IllegalArgumentException ex) {
            return new PolicyDecision.Reject("Analyzer suggested unsupported action: " + decision.actionType());
        }

        Optional<HealingStrategySpec> matchingStrategy = findMatchingStrategy(policy.getSpec().getStrategies(), actionType);
        if (matchingStrategy.isEmpty()) {
            return new PolicyDecision.Reject("Analyzer action " + actionType + " is not allowed by policy");
        }

        int currentReplicas = deployment.getSpec() == null || deployment.getSpec().getReplicas() == null
            ? 1
            : deployment.getSpec().getReplicas();

        int targetReplicas = switch (actionType) {
            case SCALE_OUT -> Math.min(
                currentReplicas + 1,
                matchingStrategy.get().getMaxReplicas() == null ? currentReplicas + 1 : matchingStrategy.get().getMaxReplicas()
            );
            case ROLLING_RESTART, RESTART_WITH_DUMP -> currentReplicas;
        };

        Map<String, Object> parameters = decision.parameters() == null ? Map.of() : decision.parameters();
        boolean heapDumpRequested = actionType == HealingActionType.RESTART_WITH_DUMP
            || Boolean.TRUE.equals(parameters.get("capture_heap_dump"));

        return new PolicyDecision.Execute(
            new ActionPlan(
                actionType,
                currentReplicas,
                targetReplicas,
                heapDumpRequested,
                decision.diagnosis(),
                decision.confidence(),
                decision.explanation()
            )
        );
    }

    private Optional<HealingStrategySpec> findMatchingStrategy(List<HealingStrategySpec> strategies, HealingActionType actionType) {
        if (strategies == null) {
            return Optional.empty();
        }
        return strategies.stream()
            .filter(strategy -> strategy.getAction() != null)
            .filter(strategy -> strategy.getAction().trim().toUpperCase(Locale.ROOT).equals(actionType.name()))
            .findFirst();
    }
}
