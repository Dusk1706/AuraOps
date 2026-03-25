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
import com.auraops.operator.infrastructure.realtime.HealerEventPublisher;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ControllerConfiguration
public class HealerPolicyReconciler implements Reconciler<HealerPolicy> {

    private static final Logger log = LoggerFactory.getLogger(HealerPolicyReconciler.class);
    private static final Duration VERIFICATION_REQUEUE_INTERVAL = Duration.ofSeconds(5);

    private final KubernetesClient kubernetesClient;
    private final KubernetesTelemetryCollector telemetryCollector;
    private final AnalyzerClient analyzerClient;
    private final PolicyDecisionService policyDecisionService;
    private final HealingSafetyService healingSafetyService;
    private final HealingRateLimiter healingRateLimiter;
    private final DeploymentActionExecutor actionExecutor;
    private final DeploymentReadinessVerifier readinessVerifier;
    private final HealerEventPublisher healerEventPublisher;

    public HealerPolicyReconciler(
        KubernetesClient kubernetesClient,
        KubernetesTelemetryCollector telemetryCollector,
        AnalyzerClient analyzerClient,
        PolicyDecisionService policyDecisionService,
        HealingSafetyService healingSafetyService,
        HealingRateLimiter healingRateLimiter,
        DeploymentActionExecutor actionExecutor,
        DeploymentReadinessVerifier readinessVerifier,
        HealerEventPublisher healerEventPublisher
    ) {
        this.kubernetesClient = Objects.requireNonNull(kubernetesClient);
        this.telemetryCollector = Objects.requireNonNull(telemetryCollector);
        this.analyzerClient = Objects.requireNonNull(analyzerClient);
        this.policyDecisionService = Objects.requireNonNull(policyDecisionService);
        this.healingSafetyService = Objects.requireNonNull(healingSafetyService);
        this.healingRateLimiter = Objects.requireNonNull(healingRateLimiter);
        this.actionExecutor = Objects.requireNonNull(actionExecutor);
        this.readinessVerifier = Objects.requireNonNull(readinessVerifier);
        this.healerEventPublisher = Objects.requireNonNull(healerEventPublisher);
    }

    @Override
    public java.util.List<EventSource<?, HealerPolicy>> prepareEventSources(EventSourceContext<HealerPolicy> context) {
        var configuration = io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration
            .from(io.fabric8.kubernetes.api.model.apps.ReplicaSet.class, HealerPolicy.class)
            .withSecondaryToPrimaryMapper(rs -> {
                if (rs.getMetadata().getOwnerReferences() == null) {
                    return Set.of();
                }
                return rs.getMetadata().getOwnerReferences().stream()
                    .filter(owner -> "Deployment".equals(owner.getKind()))
                    .flatMap(owner -> context.getPrimaryCache()
                        .list(policy -> policy.getSpec().getTargetDeployment().equals(owner.getName())))
                    .map(ResourceID::fromResource)
                    .collect(Collectors.toSet());
            })
            .build();
            
        io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource<io.fabric8.kubernetes.api.model.apps.ReplicaSet, HealerPolicy> eventSource = 
            new io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource<>(configuration, context);
            
        return java.util.List.of(eventSource);
    }

    @Override
    public UpdateControl<HealerPolicy> reconcile(HealerPolicy resource, Context<HealerPolicy> context) {
        String namespace = requireNamespace(resource);
        String targetName = requireTargetDeployment(resource);

        Deployment deployment = kubernetesClient.apps().deployments()
            .inNamespace(namespace)
            .withName(targetName)
            .get();

        if (deployment == null) {
            updateStatus(resource, HealingPhase.WAITING_FOR_TARGET, null, 0.0,
                "Target deployment %s was not found".formatted(targetName), "NONE", null);
            return UpdateControl.patchStatus(resource);
        }

        // 1. Handle ongoing verification
        if (isVerifying(resource)) {
            return handleVerification(resource, deployment);
        }

        // 2. Telemetry & Analysis
        var incidentContext = telemetryCollector.collect(deployment);
        if (isTelemetryUnavailable(incidentContext.additionalMetrics())) {
            updateStatus(resource, HealingPhase.ANALYSIS_BLOCKED, null, 0.0,
                telemetryUnavailableMessage(incidentContext.additionalMetrics()), "NONE", null);
            return UpdateControl.patchStatus(resource);
        }

        AnalyzerDecision decision = analyzerClient.analyze(incidentContext);
        if (decision instanceof AnalyzerDecision.Failure failure) {
            updateStatus(resource, HealingPhase.ANALYSIS_BLOCKED, null, 0.0, failure.message(), "NONE", null);
            return UpdateControl.patchStatus(resource);
        }
        if (decision instanceof AnalyzerDecision.Inconclusive inconclusive) {
            updateStatus(resource, HealingPhase.ANALYSIS_BLOCKED, null, 0.0, inconclusive.message(), "NONE", null);
            return UpdateControl.patchStatus(resource);
        }

        // 3. Policy & Safety
        AnalyzerDecision.Success success = (AnalyzerDecision.Success) decision;
        PolicyDecision policyDecision = policyDecisionService.evaluate(resource, deployment, success);
        if (policyDecision instanceof PolicyDecision.Reject reject) {
            updateStatus(resource, HealingPhase.POLICY_BLOCKED, success.diagnosis(), success.confidence(), reject.reason(), "NONE", null);
            return UpdateControl.patchStatus(resource);
        }

        PolicyDecision.Execute executableDecision = (PolicyDecision.Execute) policyDecision;
        PolicyDecision safetyDecision = healingSafetyService.validate(executableDecision.actionPlan(), deployment);
        if (safetyDecision instanceof PolicyDecision.Reject reject) {
            updateStatus(resource, HealingPhase.POLICY_BLOCKED, success.diagnosis(), success.confidence(), reject.reason(), "NONE", null);
            return UpdateControl.patchStatus(resource);
        }

        // 4. Rate Limiting
        if (!healingRateLimiter.tryAcquire()) {
            updateStatus(resource, HealingPhase.RATE_LIMITED, success.diagnosis(), success.confidence(),
                "Healing rate limiter rejected the action to avoid alert storms", "NONE", null);
            return UpdateControl.patchStatus(resource);
        }

        // 5. Execution
        log.info("Executing healing action {} for deployment {}/{}", 
            executableDecision.actionPlan().actionType(), namespace, targetName);
        actionExecutor.execute(deployment, executableDecision.actionPlan());

        // Refresh deployment to get the NEW generation after execution
        Deployment refreshedDeployment = kubernetesClient.apps().deployments()
            .inNamespace(namespace)
            .withName(targetName)
            .get();
        
        Long expectedGeneration = (refreshedDeployment != null && refreshedDeployment.getMetadata() != null)
            ? refreshedDeployment.getMetadata().getGeneration()
            : null;

        updateStatus(
            resource,
            HealingPhase.VERIFYING,
            success.diagnosis(),
            success.confidence(),
            "Action executed. Verifying rollout for generation " + expectedGeneration,
            executableDecision.actionPlan().actionType().name(),
            expectedGeneration
        );

        return UpdateControl.patchStatus(resource).rescheduleAfter(VERIFICATION_REQUEUE_INTERVAL);
    }

    private boolean isVerifying(HealerPolicy resource) {
        return resource.getStatus() != null && HealingPhase.VERIFYING.name().equals(resource.getStatus().getPhase());
    }

    private UpdateControl<HealerPolicy> handleVerification(HealerPolicy resource, Deployment deployment) {
        HealerPolicyStatus status = resource.getStatus();
        Long expectedGen = status.getExpectedGeneration();
        
        var verification = readinessVerifier.verify(deployment, expectedGen);
        
        if (verification.ready()) {
            log.info("Healing successful for {}/{}", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName());
            updateStatus(
                resource,
                HealingPhase.HEALED,
                status.getAiDiagnosis(),
                status.getHealthScore(),
                verification.message(),
                status.getLastAction(),
                expectedGen
            );
            return UpdateControl.patchStatus(resource);
        }

        // If not ready, we keep waiting
        status.setMessage(verification.message());
        status.setTimestamp(OffsetDateTime.now());
        return UpdateControl.patchStatus(resource).rescheduleAfter(VERIFICATION_REQUEUE_INTERVAL);
    }

    private void updateStatus(
        HealerPolicy resource,
        HealingPhase phase,
        String diagnosis,
        Double healthScore,
        String message,
        String lastAction,
        Long expectedGeneration
    ) {
        HealerPolicyStatus status = resource.getStatus() == null ? new HealerPolicyStatus() : resource.getStatus();
        status.setPhase(phase.name());
        status.setAiDiagnosis(diagnosis);
        status.setHealthScore(healthScore);
        status.setMessage(message);
        status.setLastAction(lastAction);
        status.setObservedDeployment(requireTargetDeployment(resource));
        status.setExpectedGeneration(expectedGeneration);
        status.setTimestamp(OffsetDateTime.now());
        resource.setStatus(status);

        var namespace = requireNamespace(resource);
        var policyName = requirePolicyName(resource);
        var action = requireNonBlank(lastAction, "status.lastAction");
        var eventType = switch (phase) {
            case VERIFYING -> "RECONCILIATION_STARTED";
            case HEALED -> "RECONCILIATION_COMPLETED";
            case ANALYSIS_BLOCKED, POLICY_BLOCKED, RATE_LIMITED -> "RECONCILIATION_FAILED";
            default -> "POLICY_APPLIED";
        };

        healerEventPublisher.publish(
            eventType,
            policyName,
            action,
            phase.name(),
            requireTargetDeployment(resource),
            namespace,
            message,
            diagnosis,
            healthScore
        );
    }

    private String requireNamespace(HealerPolicy resource) {
        if (resource.getMetadata() == null) {
            throw new IllegalStateException("HealerPolicy.metadata is required");
        }
        return requireNonBlank(resource.getMetadata().getNamespace(), "HealerPolicy.metadata.namespace");
    }

    private String requirePolicyName(HealerPolicy resource) {
        if (resource.getMetadata() == null) {
            throw new IllegalStateException("HealerPolicy.metadata is required");
        }
        return requireNonBlank(resource.getMetadata().getName(), "HealerPolicy.metadata.name");
    }

    private String requireTargetDeployment(HealerPolicy resource) {
        if (resource.getSpec() == null) {
            throw new IllegalStateException("HealerPolicy.spec is required");
        }
        return requireNonBlank(resource.getSpec().getTargetDeployment(), "HealerPolicy.spec.targetDeployment");
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " is required");
        }
        return value;
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
