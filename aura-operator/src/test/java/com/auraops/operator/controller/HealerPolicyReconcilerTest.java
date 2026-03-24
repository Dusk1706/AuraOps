package com.auraops.operator.controller;

import com.auraops.operator.application.ActionPlan;
import com.auraops.operator.application.AnalyzerClient;
import com.auraops.operator.application.AnalyzerDecision;
import com.auraops.operator.application.HealingRateLimiter;
import com.auraops.operator.application.HealingSafetyService;
import com.auraops.operator.application.IncidentContext;
import com.auraops.operator.application.PolicyDecision;
import com.auraops.operator.application.PolicyDecisionService;
import com.auraops.operator.crd.HealerPolicy;
import com.auraops.operator.crd.HealerPolicySpec;
import com.auraops.operator.crd.HealingStrategySpec;
import com.auraops.operator.infrastructure.kubernetes.DeploymentActionExecutor;
import com.auraops.operator.infrastructure.kubernetes.DeploymentReadinessVerifier;
import com.auraops.operator.infrastructure.kubernetes.KubernetesTelemetryCollector;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealerPolicyReconcilerTest {

    @Test
    void reconcile_executesActionWhenAnalysisAndPolicyAllowIt() {
        KubernetesClient kubernetesClient = mock(KubernetesClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        KubernetesTelemetryCollector telemetryCollector = mock(KubernetesTelemetryCollector.class);
        AnalyzerClient analyzerClient = mock(AnalyzerClient.class);
        PolicyDecisionService policyDecisionService = mock(PolicyDecisionService.class);
        HealingSafetyService healingSafetyService = mock(HealingSafetyService.class);
        HealingRateLimiter healingRateLimiter = mock(HealingRateLimiter.class);
        DeploymentActionExecutor actionExecutor = mock(DeploymentActionExecutor.class);
        DeploymentReadinessVerifier readinessVerifier = mock(DeploymentReadinessVerifier.class);
        @SuppressWarnings("unchecked")
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deployments = mock(MixedOperation.class);
        @SuppressWarnings("unchecked")
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> namespacedDeployments = mock(NonNamespaceOperation.class);
        @SuppressWarnings("unchecked")
        RollableScalableResource<Deployment> deploymentResource = mock(RollableScalableResource.class);

        HealerPolicyReconciler reconciler = new HealerPolicyReconciler(
            kubernetesClient,
            telemetryCollector,
            analyzerClient,
            policyDecisionService,
            healingSafetyService,
            healingRateLimiter,
            actionExecutor,
            readinessVerifier
        );

        HealerPolicy policy = policy();
        Deployment deployment = deployment();
        IncidentContext incidentContext = new IncidentContext("id", "payments", "payments-api", List.of("oom"), List.of(), "unknown", "unknown", 3, Map.of());
        AnalyzerDecision.Success success = new AnalyzerDecision.Success("Heap growth detected", 0.99, "ROLLING_RESTART", Map.of(), "restart");
        ActionPlan actionPlan = new ActionPlan(com.auraops.operator.domain.HealingActionType.ROLLING_RESTART, 4, 4, false, "Heap growth detected", 0.99, "restart");

        when(kubernetesClient.apps().deployments()).thenReturn(deployments);
        when(deployments.inNamespace("payments")).thenReturn(namespacedDeployments);
        when(namespacedDeployments.withName("payments-api")).thenReturn(deploymentResource);
        when(deployments.withName("payments-api")).thenReturn(deploymentResource);
        when(deploymentResource.get()).thenReturn(deployment);
        when(telemetryCollector.collect(deployment)).thenReturn(incidentContext);
        when(analyzerClient.analyze(incidentContext)).thenReturn(success);
        when(policyDecisionService.evaluate(policy, deployment, success)).thenReturn(new PolicyDecision.Execute(actionPlan));
        when(healingSafetyService.validate(actionPlan, deployment)).thenReturn(new PolicyDecision.Execute(actionPlan));
        when(healingRateLimiter.tryAcquire()).thenReturn(true);
        doNothing().when(actionExecutor).execute(deployment, actionPlan);
        when(readinessVerifier.verify("payments", "payments-api", 1L))
            .thenReturn(new DeploymentReadinessVerifier.VerificationResult(true, "Deployment passed readiness verification with 4 ready replicas and observedGeneration >= 2"));

        var result = reconciler.reconcile(policy, mock(Context.class));

        assertThat(result.isPatchStatus()).isTrue();
        assertThat(policy.getStatus().getPhase()).isEqualTo("HEALED");
        assertThat(policy.getStatus().getMessage()).contains("readiness verification");
        verify(actionExecutor).execute(deployment, actionPlan);
    }

    @Test
    void reconcile_doesNotExecuteWhenAnalyzerIsInconclusive() {
        KubernetesClient kubernetesClient = mock(KubernetesClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        KubernetesTelemetryCollector telemetryCollector = mock(KubernetesTelemetryCollector.class);
        AnalyzerClient analyzerClient = mock(AnalyzerClient.class);
        PolicyDecisionService policyDecisionService = mock(PolicyDecisionService.class);
        HealingSafetyService healingSafetyService = mock(HealingSafetyService.class);
        HealingRateLimiter healingRateLimiter = mock(HealingRateLimiter.class);
        DeploymentActionExecutor actionExecutor = mock(DeploymentActionExecutor.class);
        DeploymentReadinessVerifier readinessVerifier = mock(DeploymentReadinessVerifier.class);
        @SuppressWarnings("unchecked")
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deployments = mock(MixedOperation.class);
        @SuppressWarnings("unchecked")
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> namespacedDeployments = mock(NonNamespaceOperation.class);
        @SuppressWarnings("unchecked")
        RollableScalableResource<Deployment> deploymentResource = mock(RollableScalableResource.class);

        HealerPolicyReconciler reconciler = new HealerPolicyReconciler(
            kubernetesClient,
            telemetryCollector,
            analyzerClient,
            policyDecisionService,
            healingSafetyService,
            healingRateLimiter,
            actionExecutor,
            readinessVerifier
        );

        HealerPolicy policy = policy();
        Deployment deployment = deployment();
        IncidentContext incidentContext = new IncidentContext("id", "payments", "payments-api", List.of("oom"), List.of(), "unknown", "unknown", 3, Map.of());

        when(kubernetesClient.apps().deployments()).thenReturn(deployments);
        when(deployments.inNamespace("payments")).thenReturn(namespacedDeployments);
        when(namespacedDeployments.withName("payments-api")).thenReturn(deploymentResource);
        when(deployments.withName("payments-api")).thenReturn(deploymentResource);
        when(deploymentResource.get()).thenReturn(deployment);
        when(telemetryCollector.collect(deployment)).thenReturn(incidentContext);
        when(analyzerClient.analyze(incidentContext)).thenReturn(new AnalyzerDecision.Inconclusive("AI_LOW_CONFIDENCE", "Low data", true));

        reconciler.reconcile(policy, mock(Context.class));

        assertThat(policy.getStatus().getPhase()).isEqualTo("ANALYSIS_BLOCKED");
        verify(actionExecutor, never()).execute(any(), any());
    }

    @Test
    void reconcile_marksFailedWhenDeploymentDoesNotRecoverReadiness() {
        KubernetesClient kubernetesClient = mock(KubernetesClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        KubernetesTelemetryCollector telemetryCollector = mock(KubernetesTelemetryCollector.class);
        AnalyzerClient analyzerClient = mock(AnalyzerClient.class);
        PolicyDecisionService policyDecisionService = mock(PolicyDecisionService.class);
        HealingSafetyService healingSafetyService = mock(HealingSafetyService.class);
        HealingRateLimiter healingRateLimiter = mock(HealingRateLimiter.class);
        DeploymentActionExecutor actionExecutor = mock(DeploymentActionExecutor.class);
        DeploymentReadinessVerifier readinessVerifier = mock(DeploymentReadinessVerifier.class);
        @SuppressWarnings("unchecked")
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deployments = mock(MixedOperation.class);
        @SuppressWarnings("unchecked")
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> namespacedDeployments = mock(NonNamespaceOperation.class);
        @SuppressWarnings("unchecked")
        RollableScalableResource<Deployment> deploymentResource = mock(RollableScalableResource.class);

        HealerPolicyReconciler reconciler = new HealerPolicyReconciler(
            kubernetesClient,
            telemetryCollector,
            analyzerClient,
            policyDecisionService,
            healingSafetyService,
            healingRateLimiter,
            actionExecutor,
            readinessVerifier
        );

        HealerPolicy policy = policy();
        Deployment deployment = deployment();
        IncidentContext incidentContext = new IncidentContext("id", "payments", "payments-api", List.of("oom"), List.of(), "unknown", "unknown", 3, Map.of());
        AnalyzerDecision.Success success = new AnalyzerDecision.Success("Heap growth detected", 0.99, "ROLLING_RESTART", Map.of(), "restart");
        ActionPlan actionPlan = new ActionPlan(com.auraops.operator.domain.HealingActionType.ROLLING_RESTART, 4, 4, false, "Heap growth detected", 0.99, "restart");

        when(kubernetesClient.apps().deployments()).thenReturn(deployments);
        when(deployments.inNamespace("payments")).thenReturn(namespacedDeployments);
        when(namespacedDeployments.withName("payments-api")).thenReturn(deploymentResource);
        when(deployments.withName("payments-api")).thenReturn(deploymentResource);
        when(deploymentResource.get()).thenReturn(deployment);
        when(telemetryCollector.collect(deployment)).thenReturn(incidentContext);
        when(analyzerClient.analyze(incidentContext)).thenReturn(success);
        when(policyDecisionService.evaluate(policy, deployment, success)).thenReturn(new PolicyDecision.Execute(actionPlan));
        when(healingSafetyService.validate(actionPlan, deployment)).thenReturn(new PolicyDecision.Execute(actionPlan));
        when(healingRateLimiter.tryAcquire()).thenReturn(true);
        when(readinessVerifier.verify("payments", "payments-api", 1L))
            .thenReturn(new DeploymentReadinessVerifier.VerificationResult(false, "Deployment did not become ready within PT30S"));

        reconciler.reconcile(policy, mock(Context.class));

        assertThat(policy.getStatus().getPhase()).isEqualTo("FAILED");
        assertThat(policy.getStatus().getMessage()).contains("did not become ready");
        verify(actionExecutor).execute(deployment, actionPlan);
    }

    @Test
    void reconcile_blocksHealingWhenTelemetryIsUnavailable() {
        KubernetesClient kubernetesClient = mock(KubernetesClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        KubernetesTelemetryCollector telemetryCollector = mock(KubernetesTelemetryCollector.class);
        AnalyzerClient analyzerClient = mock(AnalyzerClient.class);
        PolicyDecisionService policyDecisionService = mock(PolicyDecisionService.class);
        HealingSafetyService healingSafetyService = mock(HealingSafetyService.class);
        HealingRateLimiter healingRateLimiter = mock(HealingRateLimiter.class);
        DeploymentActionExecutor actionExecutor = mock(DeploymentActionExecutor.class);
        DeploymentReadinessVerifier readinessVerifier = mock(DeploymentReadinessVerifier.class);
        @SuppressWarnings("unchecked")
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deployments = mock(MixedOperation.class);
        @SuppressWarnings("unchecked")
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> namespacedDeployments = mock(NonNamespaceOperation.class);
        @SuppressWarnings("unchecked")
        RollableScalableResource<Deployment> deploymentResource = mock(RollableScalableResource.class);

        HealerPolicyReconciler reconciler = new HealerPolicyReconciler(
            kubernetesClient,
            telemetryCollector,
            analyzerClient,
            policyDecisionService,
            healingSafetyService,
            healingRateLimiter,
            actionExecutor,
            readinessVerifier
        );

        HealerPolicy policy = policy();
        Deployment deployment = deployment();
        IncidentContext incidentContext = new IncidentContext(
            "id",
            "payments",
            "payments-api",
            List.of(),
            List.of(),
            "unknown",
            "unknown",
            0,
            Map.of(
                KubernetesTelemetryCollector.TELEMETRY_STATUS_KEY,
                KubernetesTelemetryCollector.TELEMETRY_UNAVAILABLE,
                KubernetesTelemetryCollector.TELEMETRY_REASONS_KEY,
                List.of("LOKI_UNAVAILABLE: timeout")
            )
        );

        when(kubernetesClient.apps().deployments()).thenReturn(deployments);
        when(deployments.inNamespace("payments")).thenReturn(namespacedDeployments);
        when(namespacedDeployments.withName("payments-api")).thenReturn(deploymentResource);
        when(deploymentResource.get()).thenReturn(deployment);
        when(telemetryCollector.collect(deployment)).thenReturn(incidentContext);

        reconciler.reconcile(policy, mock(Context.class));

        assertThat(policy.getStatus().getPhase()).isEqualTo("ANALYSIS_BLOCKED");
        assertThat(policy.getStatus().getMessage()).contains("Telemetry unavailable");
        verify(analyzerClient, never()).analyze(any());
        verify(actionExecutor, never()).execute(any(), any());
    }

    private HealerPolicy policy() {
        HealerPolicy policy = new HealerPolicy();
        policy.setMetadata(new ObjectMetaBuilder().withName("payments-auto-heal").withNamespace("payments").build());
        HealerPolicySpec spec = new HealerPolicySpec();
        spec.setTargetDeployment("payments-api");
        HealingStrategySpec strategy = new HealingStrategySpec();
        strategy.setType("MemoryLeak");
        strategy.setAction("ROLLING_RESTART");
        spec.setStrategies(List.of(strategy));
        policy.setSpec(spec);
        return policy;
    }

    private Deployment deployment() {
        Deployment deployment = new Deployment();
        deployment.setMetadata(new ObjectMetaBuilder().withName("payments-api").withNamespace("payments").withGeneration(1L).build());
        deployment.setSpec(new DeploymentSpecBuilder().withReplicas(4).build());
        return deployment;
    }
}
