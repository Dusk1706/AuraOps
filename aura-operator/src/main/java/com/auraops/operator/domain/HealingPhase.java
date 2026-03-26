package com.auraops.operator.domain;

public enum HealingPhase {
    WAITING_FOR_TARGET,
    ANALYZING,
    ANALYSIS_BLOCKED,
    POLICY_BLOCKED,
    PENDING_APPROVAL,
    RATE_LIMITED,
    VERIFYING,
    HEALED,
    FAILED
}
