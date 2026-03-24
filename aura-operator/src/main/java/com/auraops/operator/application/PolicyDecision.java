package com.auraops.operator.application;

public sealed interface PolicyDecision permits PolicyDecision.Execute, PolicyDecision.Reject {

    record Execute(ActionPlan actionPlan) implements PolicyDecision {}

    record Reject(String reason) implements PolicyDecision {}
}
