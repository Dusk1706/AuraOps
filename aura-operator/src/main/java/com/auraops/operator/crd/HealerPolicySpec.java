package com.auraops.operator.crd;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class HealerPolicySpec {

    @NotBlank
    private String targetDeployment;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double aiConfidenceThreshold = 0.95;

    private boolean approvalRequired = false;

    @Valid
    private List<HealingStrategySpec> strategies = new ArrayList<>();

    public String getTargetDeployment() {
        return targetDeployment;
    }

    public void setTargetDeployment(String targetDeployment) {
        this.targetDeployment = targetDeployment;
    }

    public double getAiConfidenceThreshold() {
        return aiConfidenceThreshold;
    }

    public void setAiConfidenceThreshold(double aiConfidenceThreshold) {
        this.aiConfidenceThreshold = aiConfidenceThreshold;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public List<HealingStrategySpec> getStrategies() {
        return strategies;
    }

    public void setStrategies(List<HealingStrategySpec> strategies) {
        this.strategies = strategies == null ? new ArrayList<>() : new ArrayList<>(strategies);
    }
}
