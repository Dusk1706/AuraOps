package com.auraops.operator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auraops.healer")
public class HealerProperties {

    private double maxImpactRatio = 0.25d;
    private int logLinesPerPod = 100;
    private int maxTotalLogLines = 500;
    private Duration verificationTimeout = Duration.ofSeconds(30);
    private Duration verificationPollInterval = Duration.ofSeconds(2);

    public double getMaxImpactRatio() {
        return maxImpactRatio;
    }

    public void setMaxImpactRatio(double maxImpactRatio) {
        this.maxImpactRatio = maxImpactRatio;
    }

    public int getLogLinesPerPod() {
        return logLinesPerPod;
    }

    public void setLogLinesPerPod(int logLinesPerPod) {
        this.logLinesPerPod = logLinesPerPod;
    }

    public int getMaxTotalLogLines() {
        return maxTotalLogLines;
    }

    public void setMaxTotalLogLines(int maxTotalLogLines) {
        this.maxTotalLogLines = maxTotalLogLines;
    }

    public Duration getVerificationTimeout() {
        return verificationTimeout;
    }

    public void setVerificationTimeout(Duration verificationTimeout) {
        this.verificationTimeout = verificationTimeout;
    }

    public Duration getVerificationPollInterval() {
        return verificationPollInterval;
    }

    public void setVerificationPollInterval(Duration verificationPollInterval) {
        this.verificationPollInterval = verificationPollInterval;
    }
}
