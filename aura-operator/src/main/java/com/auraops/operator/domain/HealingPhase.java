package com.auraops.operator.domain;

public enum HealingPhase {
    WAITING_FOR_TARGET,
    ANALYZING,
    ANALYSIS_BLOCKED,
    POLICY_BLOCKED,
    RATE_LIMITED,
    HEALED,
    FAILED
}
