package com.auraops.operator.crd;

import java.time.OffsetDateTime;

public class HealerPolicyStatus {

    private String phase;
    private String message;
    private String lastAction;
    private String aiDiagnosis;
    private Double healthScore;
    private String observedDeployment;
    private OffsetDateTime timestamp;

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }

    public String getAiDiagnosis() {
        return aiDiagnosis;
    }

    public void setAiDiagnosis(String aiDiagnosis) {
        this.aiDiagnosis = aiDiagnosis;
    }

    public Double getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(Double healthScore) {
        this.healthScore = healthScore;
    }

    public String getObservedDeployment() {
        return observedDeployment;
    }

    public void setObservedDeployment(String observedDeployment) {
        this.observedDeployment = observedDeployment;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
