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
import com.auraops.operator.crd.HealerPolicyStatus;
import com.auraops.operator.crd.HealerPolicySpec;
import com.auraops.operator.crd.HealingStrategySpec;
import com.auraops.operator.domain.HealingPhase;
import com.auraops.operator.infrastructure.kubernetes.DeploymentActionExecutor;
import com.auraops.operator.infrastructure.kubernetes.DeploymentReadinessVerifier;
import com.auraops.operator.infrastructure.kubernetes.KubernetesTelemetryCollector;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealerPolicyReconcilerTest {

    private KubernetesClient kubernetesClient;
    private KubernetesTelemetryCollector telemetryCollector;
    private AnalyzerClient analyzerClient;
    private PolicyDecisionService policyDecisionService;
    private HealingSafetyService healingSafetyService;
    private HealingRateLimiter healingRateLimiter;
    private DeploymentActionExecutor actionExecutor;
    private DeploymentReadinessVerifier readinessVerifier;
    private HealerPolicyReconciler reconciler;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kubernetesClient = mock(KubernetesClient.class, Answers.RETURNS_DEEP_STUBS);
        telemetryCollector = mock(KubernetesTelemetryCollector.class);
        analyzerClient = mock(AnalyzerClient.class);
        policyDecisionService = mock(PolicyDecisionService.class);
        healingSafetyService = mock(HealingSafetyService.class);
        healingRateLimiter = mock(HealingRateLimiter.class);
        actionExecutor = mock(DeploymentActionExecutor.class);
        readinessVerifier = mock(DeploymentReadinessVerifier.class);

        reconciler = new HealerPolicyReconciler(
            kubernetesClient,
            telemetryCollector,
            analyzerClient,
            policyDecisionService,
            healingSafetyService,
            healingRateLimiter,
            actionExecutor,
            readinessVerifier
        );
    }

    @Test
    void reconcile_transitionsToVerifyingAfterExecutingAction() {
        HealerPolicy policy = policy();
        Deployment deployment = deployment(1L);
        IncidentContext incidentContext = new IncidentContext("id", "payments", "payments-api", List.of("oom"), List.of(), "unknown", "unknown", 3, Map.of());
        AnalyzerDecision.Success success = new AnalyzerDecision.Success("Heap growth detected", 0.99, "ROLLING_RESTART", Map.of(), "restart");
        ActionPlan actionPlan = new ActionPlan(com.auraops.operator.domain.HealingActionType.ROLLING_RESTART, 4, 4, false, "Heap growth detected", 0.99, "restart");

        setupDeploymentMock("payments", "payments-api", deployment);
        when(telemetryCollector.collect(deployment)).thenReturn(incidentContext);
        when(analyzerClient.analyze(incidentContext)).thenReturn(success);
        when(policyDecisionService.evaluate(policy, deployment, success)).thenReturn(new PolicyDecision.Execute(actionPlan));
        when(healingSafetyService.validate(actionPlan, deployment)).thenReturn(new PolicyDecision.Execute(actionPlan));
        when(healingRateLimiter.tryAcquire()).thenReturn(true);
        
        // After execute, the operator refreshes deployment. Let's say generation increased to 2
        Deployment refreshedDeployment = deployment(2L);
        when(kubernetesClient.apps().deployments().inNamespace("payments").withName("payments-api").get())
            .thenReturn(deployment, refreshedDeployment);

        var result = reconciler.reconcile(policy, mock(Context.class));

        assertThat(result.isPatchStatus()).isTrue();
        assertThat(result.getScheduleDelay()).isPresent();
        assertThat(policy.getStatus().getPhase()).isEqualTo("VERIFYING");
        assertThat(policy.getStatus().getExpectedGeneration()).isEqualTo(2L);
        verify(actionExecutor).execute(deployment, actionPlan);
    }

    @Test
    void reconcile_transitionsToHealedWhenVerifyingAndDeploymentIsReady() {
        HealerPolicy policy = policy();
        HealerPolicyStatus status = new HealerPolicyStatus();
        status.setPhase(HealingPhase.VERIFYING.name());
        status.setExpectedGeneration(2L);
        status.setAiDiagnosis("Memory leak");
        policy.setStatus(status);

        Deployment deployment = deployment(2L);
        setupDeploymentMock("payments", "payments-api", deployment);

        when(readinessVerifier.verify(eq(deployment), eq(2L)))
            .thenReturn(new DeploymentReadinessVerifier.VerificationResult(true, "Deployment is ready"));

        var result = reconciler.reconcile(policy, mock(Context.class));

        assertThat(result.isPatchStatus()).isTrue();
        assertThat(result.getScheduleDelay()).isEmpty();
        assertThat(policy.getStatus().getPhase()).isEqualTo("HEALED");
        assertThat(policy.getStatus().getMessage()).isEqualTo("Deployment is ready");
    }

    @Test
    void reconcile_staysInVerifyingWhenDeploymentNotReadyYet() {
        HealerPolicy policy = policy();
        HealerPolicyStatus status = new HealerPolicyStatus();
        status.setPhase(HealingPhase.VERIFYING.name());
        status.setExpectedGeneration(2L);
        policy.setStatus(status);

        Deployment deployment = deployment(2L);
        setupDeploymentMock("payments", "payments-api", deployment);

        when(readinessVerifier.verify(eq(deployment), eq(2L)))
            .thenReturn(new DeploymentReadinessVerifier.VerificationResult(false, "Waiting for replicas"));

        var result = reconciler.reconcile(policy, mock(Context.class));

        assertThat(result.isPatchStatus()).isTrue();
        assertThat(result.getScheduleDelay()).isPresent();
        assertThat(policy.getStatus().getPhase()).isEqualTo("VERIFYING");
        assertThat(policy.getStatus().getMessage()).isEqualTo("Waiting for replicas");
    }

    @Test
    void reconcile_doesNotExecuteWhenAnalyzerIsInconclusive() {
        HealerPolicy policy = policy();
        Deployment deployment = deployment(1L);
        IncidentContext incidentContext = new IncidentContext("id", "payments", "payments-api", List.of("oom"), List.of(), "unknown", "unknown", 3, Map.of());

        setupDeploymentMock("payments", "payments-api", deployment);
        when(telemetryCollector.collect(deployment)).thenReturn(incidentContext);
        when(analyzerClient.analyze(incidentContext)).thenReturn(new AnalyzerDecision.Inconclusive("AI_LOW_CONFIDENCE", "Low data", true));

        reconciler.reconcile(policy, mock(Context.class));

        assertThat(policy.getStatus().getPhase()).isEqualTo("ANALYSIS_BLOCKED");
        verify(actionExecutor, never()).execute(any(), any());
    }

    @Test
    void reconcile_blocksHealingWhenTelemetryIsUnavailable() {
        HealerPolicy policy = policy();
        Deployment deployment = deployment(1L);
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

        setupDeploymentMock("payments", "payments-api", deployment);
        when(telemetryCollector.collect(deployment)).thenReturn(incidentContext);

        reconciler.reconcile(policy, mock(Context.class));

        assertThat(policy.getStatus().getPhase()).isEqualTo("ANALYSIS_BLOCKED");
        assertThat(policy.getStatus().getMessage()).contains("Telemetry unavailable");
        verify(analyzerClient, never()).analyze(any());
        verify(actionExecutor, never()).execute(any(), any());
    }

    @SuppressWarnings("unchecked")
    private void setupDeploymentMock(String namespace, String name, Deployment deployment) {
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deployments = mock(MixedOperation.class);
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> namespacedDeployments = mock(NonNamespaceOperation.class);
        RollableScalableResource<Deployment> deploymentResource = mock(RollableScalableResource.class);

        when(kubernetesClient.apps().deployments()).thenReturn(deployments);
        when(deployments.inNamespace(namespace)).thenReturn(namespacedDeployments);
        when(namespacedDeployments.withName(name)).thenReturn(deploymentResource);
        when(deploymentResource.get()).thenReturn(deployment);
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

    private Deployment deployment(Long generation) {
        Deployment deployment = new Deployment();
        deployment.setMetadata(new ObjectMetaBuilder()
            .withName("payments-api")
            .withNamespace("payments")
            .withGeneration(generation)
            .build());
        deployment.setSpec(new DeploymentSpecBuilder().withReplicas(4).build());
        deployment.setStatus(new DeploymentStatusBuilder()
            .withObservedGeneration(generation)
            .withReadyReplicas(4)
            .withAvailableReplicas(4)
            .build());
        return deployment;
    }
}
