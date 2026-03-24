package com.auraops.operator.domain;

import java.util.Locale;

public enum HealingActionType {
    ROLLING_RESTART,
    RESTART_WITH_DUMP,
    SCALE_OUT;

    public static HealingActionType fromAnalyzerValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("analyzer action type cannot be blank");
        }
        return HealingActionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
