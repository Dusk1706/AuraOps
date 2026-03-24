package com.auraops.operator.crd;

import jakarta.validation.constraints.NotBlank;

public class HealingStrategySpec {

    @NotBlank
    private String type;

    @NotBlank
    private String action;

    private String threshold;

    private Integer maxReplicas;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getThreshold() {
        return threshold;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }

    public Integer getMaxReplicas() {
        return maxReplicas;
    }

    public void setMaxReplicas(Integer maxReplicas) {
        this.maxReplicas = maxReplicas;
    }
}
