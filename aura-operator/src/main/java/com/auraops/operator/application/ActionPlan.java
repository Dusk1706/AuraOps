package com.auraops.operator.application;

import com.auraops.operator.domain.HealingActionType;

public record ActionPlan(
    HealingActionType actionType,
    int currentReplicas,
    int targetReplicas,
    boolean requestHeapDump,
    String diagnosis,
    double confidence,
    String explanation
) {
}
