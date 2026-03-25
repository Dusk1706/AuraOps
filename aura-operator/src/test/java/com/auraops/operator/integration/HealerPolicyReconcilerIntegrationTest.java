package com.auraops.operator.integration;

import com.auraops.operator.application.AnalyzerClient;
import com.auraops.operator.application.AnalyzerDecision;
import com.auraops.operator.application.HealingRateLimiter;
import com.auraops.operator.application.HealingSafetyService;
import com.auraops.operator.application.PolicyDecisionService;
import com.auraops.operator.config.HealerProperties;
import com.auraops.operator.config.ObservabilityProperties;
import com.auraops.operator.controller.HealerPolicyReconciler;
import com.auraops.operator.crd.HealerPolicy;
import com.auraops.operator.crd.HealerPolicySpec;
import com.auraops.operator.crd.HealingStrategySpec;
import com.auraops.operator.infrastructure.kubernetes.DeploymentActionExecutor;
import com.auraops.operator.infrastructure.kubernetes.DeploymentReadinessVerifier;
import com.auraops.operator.infrastructure.kubernetes.KubernetesTelemetryCollector;
import com.auraops.operator.infrastructure.observability.LokiLogCollector;
import com.auraops.operator.infrastructure.observability.TempoTraceCollector;
import com.auraops.operator.infrastructure.realtime.HealerEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.RollingUpdateDeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnableKubernetesMockClient(crud = true)
class HealerPolicyReconcilerIntegrationTest {

    @SuppressWarnings("unused")
    private KubernetesMockServer kubernetesServer;
    private KubernetesClient client;

    @Test
    void reconcile_rollsDeploymentAndUpdatesPolicyStatusAgainstSimulatedCluster() {
        client.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName("payments").endMetadata().build()).create();

        Deployment deployment = new DeploymentBuilder()
            .withMetadata(new ObjectMetaBuilder().withName("payments-api").withNamespace("payments").build())
            .withNewSpec()
                .withReplicas(4)
                .withNewSelector()
                    .addToMatchLabels("app", "payments-api")
                .endSelector()
                .withNewStrategy()
                    .withType("RollingUpdate")
                    .withRollingUpdate(new RollingUpdateDeploymentBuilder().withMaxUnavailable(new IntOrString("25%")).build())
                .endStrategy()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels("app", "payments-api")
                    .endMetadata()
                    .withNewSpec()
                        .addNewContainer()
                            .withName("app")
                            .withImage("nginx:1.27")
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .withNewStatus()
                .withObservedGeneration(1L)
                .withReadyReplicas(4)
                .withAvailableReplicas(4)
                .withUpdatedReplicas(4)
                .withUnavailableReplicas(0)
            .endStatus()
            .build();
        client.apps().deployments().resource(deployment).create();

        client.pods().resource(new PodBuilder()
            .withMetadata(new ObjectMetaBuilder()
                .withName("payments-api-7d8f6f7d9b-abc12")
                .withNamespace("payments")
                .addToLabels("app", "payments-api")
                .build())
            .withNewStatus()
                .withPhase("Running")
                .addNewContainerStatus()
                    .withName("app")
                    .withRestartCount(3)
                    .withNewState()
                        .withNewWaiting()
                            .withReason("CrashLoopBackOff")
                        .endWaiting()
                    .endState()
                .endContainerStatus()
            .endStatus()
            .build()).create();

        HealerProperties healerProperties = new HealerProperties();
        ObservabilityProperties observabilityProperties = new ObservabilityProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        KubernetesTelemetryCollector telemetryCollector = new KubernetesTelemetryCollector(
            client,
            healerProperties,
            new LokiLogCollector(RestClient.builder().baseUrl("http://localhost").build(), observabilityProperties, objectMapper),
            new TempoTraceCollector(RestClient.builder().baseUrl("http://localhost").build(), observabilityProperties, objectMapper)
        );
        AnalyzerClient analyzerClient = incidentContext -> new AnalyzerDecision.Success(
            "Heap growth detected with repeated restart loops",
            0.99,
            "ROLLING_RESTART",
            Map.of("capture_heap_dump", true),
            "Rolling restart is deterministic and stays within the deployment strategy budget."
        );

        DeploymentReadinessVerifier readinessVerifier = mock(DeploymentReadinessVerifier.class);
        when(readinessVerifier.verify(any(Deployment.class), any()))
            .thenReturn(new DeploymentReadinessVerifier.VerificationResult(true, "Deployment passed readiness verification with 4 ready replicas and observedGeneration >= 1"));

        HealerPolicyReconciler reconciler = new HealerPolicyReconciler(
            client,
            telemetryCollector,
            analyzerClient,
            new PolicyDecisionService(),
            new HealingSafetyService(healerProperties),
            new HealingRateLimiter(RateLimiterRegistry.ofDefaults()),
            new DeploymentActionExecutor(client),
            readinessVerifier,
            mock(HealerEventPublisher.class)
        );

        HealerPolicy policy = healerPolicy();

        var result = reconciler.reconcile(policy, mock(Context.class));
        Deployment updated = client.apps().deployments().inNamespace("payments").withName("payments-api").get();

        assertThat(result.isPatchStatus()).isTrue();
        assertThat(policy.getStatus()).isNotNull();
        assertThat(policy.getStatus().getPhase()).isEqualTo("VERIFYING");
        assertThat(policy.getStatus().getLastAction()).isEqualTo("ROLLING_RESTART");
        assertThat(updated.getSpec().getTemplate().getMetadata().getAnnotations())
            .containsKeys("auraops.io/restartedAt", "auraops.io/heapDumpRequested");

        // Second pass: mock verification complete
        var secondResult = reconciler.reconcile(policy, mock(Context.class));
        
        assertThat(secondResult.isPatchStatus()).isTrue();
        assertThat(policy.getStatus().getPhase()).isEqualTo("HEALED");
    }

    private HealerPolicy healerPolicy() {
        HealerPolicy policy = new HealerPolicy();
        policy.setMetadata(new ObjectMetaBuilder().withName("payments-api-auto-heal").withNamespace("payments").build());
        HealerPolicySpec spec = new HealerPolicySpec();
        spec.setTargetDeployment("payments-api");
        spec.setAiConfidenceThreshold(0.95);

        HealingStrategySpec strategy = new HealingStrategySpec();
        strategy.setType("MemoryLeak");
        strategy.setAction("ROLLING_RESTART");
        spec.setStrategies(List.of(strategy));

        policy.setSpec(spec);
        return policy;
    }
}
