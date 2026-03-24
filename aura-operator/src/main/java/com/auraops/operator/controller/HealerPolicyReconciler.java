package com.auraops.operator.controller;

import com.auraops.operator.application.AnalyzerClient;
import com.auraops.operator.application.AnalyzerDecision;
import com.auraops.operator.application.HealingRateLimiter;
import com.auraops.operator.application.HealingSafetyService;
import com.auraops.operator.application.PolicyDecision;
import com.auraops.operator.application.PolicyDecisionService;
import com.auraops.operator.crd.HealerPolicy;
import com.auraops.operator.crd.HealerPolicyStatus;
import com.auraops.operator.domain.HealingPhase;
import com.auraops.operator.infrastructure.kubernetes.DeploymentActionExecutor;
import com.auraops.operator.infrastructure.kubernetes.DeploymentReadinessVerifier;
import com.auraops.operator.infrastructure.kubernetes.KubernetesTelemetryCollector;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@ControllerConfiguration
public class HealerPolicyReconciler implements Reconciler<HealerPolicy> {

    private final KubernetesClient kubernetesClient;
    private final KubernetesTelemetryCollector telemetryCollector;
    private final AnalyzerClient analyzerClient;
    private final PolicyDecisionService policyDecisionService;
    private final HealingSafetyService healingSafetyService;
    private final HealingRateLimiter healingRateLimiter;
    private final DeploymentActionExecutor actionExecutor;
    private final DeploymentReadinessVerifier readinessVerifier;

    public HealerPolicyReconciler(
        KubernetesClient kubernetesClient,
        KubernetesTelemetryCollector telemetryCollector,
        AnalyzerClient analyzerClient,
        PolicyDecisionService policyDecisionService,
        HealingSafetyService healingSafetyService,
        HealingRateLimiter healingRateLimiter,
        DeploymentActionExecutor actionExecutor,
        DeploymentReadinessVerifier readinessVerifier
    ) {
        this.kubernetesClient = Objects.requireNonNull(kubernetesClient);
        this.telemetryCollector = Objects.requireNonNull(telemetryCollector);
        this.analyzerClient = Objects.requireNonNull(analyzerClient);
        this.policyDecisionService = Objects.requireNonNull(policyDecisionService);
        this.healingSafetyService = Objects.requireNonNull(healingSafetyService);
        this.healingRateLimiter = Objects.requireNonNull(healingRateLimiter);
        this.actionExecutor = Objects.requireNonNull(actionExecutor);
        this.readinessVerifier = Objects.requireNonNull(readinessVerifier);
    }

    @Override
    public UpdateControl<HealerPolicy> reconcile(HealerPolicy resource, Context<HealerPolicy> context) {
        Deployment deployment = kubernetesClient.apps().deployments()
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getSpec().getTargetDeployment())
            .get();

        if (deployment == null) {
            updateStatus(resource, HealingPhase.WAITING_FOR_TARGET, null, 0.0,
                "Target deployment %s was not found".formatted(resource.getSpec().getTargetDeployment()), null);
            return UpdateControl.patchStatus(resource);
        }

        var incidentContext = telemetryCollector.collect(deployment);
        if (isTelemetryUnavailable(incidentContext.additionalMetrics())) {
            updateStatus(
                resource,
                HealingPhase.ANALYSIS_BLOCKED,
                null,
                0.0,
                telemetryUnavailableMessage(incidentContext.additionalMetrics()),
                null
            );
            return UpdateControl.patchStatus(resource);
        }

        AnalyzerDecision decision = analyzerClient.analyze(incidentContext);
        if (decision instanceof AnalyzerDecision.Failure failure) {
            updateStatus(resource, HealingPhase.ANALYSIS_BLOCKED, null, 0.0, failure.message(), null);
            return UpdateControl.patchStatus(resource);
        }
        if (decision instanceof AnalyzerDecision.Inconclusive inconclusive) {
            updateStatus(resource, HealingPhase.ANALYSIS_BLOCKED, null, 0.0, inconclusive.message(), null);
            return UpdateControl.patchStatus(resource);
        }

        AnalyzerDecision.Success success = (AnalyzerDecision.Success) decision;
        PolicyDecision policyDecision = policyDecisionService.evaluate(resource, deployment, success);
        if (policyDecision instanceof PolicyDecision.Reject reject) {
            updateStatus(resource, HealingPhase.POLICY_BLOCKED, success.diagnosis(), success.confidence(), reject.reason(), null);
            return UpdateControl.patchStatus(resource);
        }

        PolicyDecision.Execute executableDecision = (PolicyDecision.Execute) policyDecision;
        PolicyDecision safetyDecision = healingSafetyService.validate(executableDecision.actionPlan(), deployment);
        if (safetyDecision instanceof PolicyDecision.Reject reject) {
            updateStatus(resource, HealingPhase.POLICY_BLOCKED, success.diagnosis(), success.confidence(), reject.reason(), null);
            return UpdateControl.patchStatus(resource);
        }

        if (!healingRateLimiter.tryAcquire()) {
            updateStatus(resource, HealingPhase.RATE_LIMITED, success.diagnosis(), success.confidence(),
                "Healing rate limiter rejected the action to avoid alert storms", null);
            return UpdateControl.patchStatus(resource);
        }

        actionExecutor.execute(deployment, executableDecision.actionPlan());

        Deployment refreshedDeployment = kubernetesClient.apps().deployments()
            .inNamespace(deployment.getMetadata().getNamespace())
            .withName(deployment.getMetadata().getName())
            .get();

        Long expectedObservedGeneration = null;
        if (refreshedDeployment != null
            && refreshedDeployment.getMetadata() != null
            && refreshedDeployment.getMetadata().getGeneration() != null) {
            expectedObservedGeneration = refreshedDeployment.getMetadata().getGeneration();
        }

        DeploymentReadinessVerifier.VerificationResult verification = readinessVerifier.verify(
            deployment.getMetadata().getNamespace(),
            deployment.getMetadata().getName(),
            expectedObservedGeneration
        );
        if (!verification.ready()) {
            updateStatus(
                resource,
                HealingPhase.FAILED,
                success.diagnosis(),
                success.confidence(),
                verification.message(),
                executableDecision.actionPlan().actionType().name()
            );
            return UpdateControl.patchStatus(resource);
        }
        updateStatus(
            resource,
            HealingPhase.HEALED,
            success.diagnosis(),
            success.confidence(),
            verification.message(),
            executableDecision.actionPlan().actionType().name()
        );
        return UpdateControl.patchStatus(resource);
    }

    private void updateStatus(
        HealerPolicy resource,
        HealingPhase phase,
        String diagnosis,
        double healthScore,
        String message,
        String lastAction
    ) {
        HealerPolicyStatus status = resource.getStatus() == null ? new HealerPolicyStatus() : resource.getStatus();
        status.setPhase(phase.name());
        status.setAiDiagnosis(diagnosis);
        status.setHealthScore(healthScore);
        status.setMessage(message);
        status.setLastAction(lastAction);
        status.setObservedDeployment(resource.getSpec().getTargetDeployment());
        status.setTimestamp(OffsetDateTime.now());
        resource.setStatus(status);
    }
    private boolean isTelemetryUnavailable(Map<String, Object> additionalMetrics) {
        if (additionalMetrics == null) {
            return false;
        }
        Object status = additionalMetrics.get(KubernetesTelemetryCollector.TELEMETRY_STATUS_KEY);
        return KubernetesTelemetryCollector.TELEMETRY_UNAVAILABLE.equals(String.valueOf(status));
    }

    private String telemetryUnavailableMessage(Map<String, Object> additionalMetrics) {
        if (additionalMetrics == null) {
            return "Telemetry is unavailable";
        }
        Object reasons = additionalMetrics.get(KubernetesTelemetryCollector.TELEMETRY_REASONS_KEY);
        if (reasons instanceof List<?> values && !values.isEmpty()) {
            return "Telemetry unavailable: " + String.join("; ", values.stream().map(String::valueOf).toList());
        }
        return "Telemetry unavailable: observability backends are unreachable";
    }
}
