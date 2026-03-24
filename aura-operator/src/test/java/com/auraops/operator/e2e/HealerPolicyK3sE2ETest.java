package com.auraops.operator.e2e;

import com.auraops.operator.application.HealingRateLimiter;
import com.auraops.operator.application.HealingSafetyService;
import com.auraops.operator.application.PolicyDecisionService;
import com.auraops.operator.config.AnalyzerClientProperties;
import com.auraops.operator.config.HealerProperties;
import com.auraops.operator.config.ObservabilityProperties;
import com.auraops.operator.controller.HealerPolicyReconciler;
import com.auraops.operator.crd.HealerPolicy;
import com.auraops.operator.crd.HealerPolicySpec;
import com.auraops.operator.crd.HealingStrategySpec;
import com.auraops.operator.infrastructure.analyzer.AnalyzerRestClient;
import com.auraops.operator.infrastructure.kubernetes.DeploymentActionExecutor;
import com.auraops.operator.infrastructure.kubernetes.DeploymentReadinessVerifier;
import com.auraops.operator.infrastructure.kubernetes.KubernetesTelemetryCollector;
import com.auraops.operator.infrastructure.observability.LokiLogCollector;
import com.auraops.operator.infrastructure.observability.TempoTraceCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.RestClient;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.DockerClientFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

class HealerPolicyK3sE2ETest {

    private static final int ANALYZER_PORT = 18080;

    private static K3sContainer k3s;
    private static KubernetesClient kubernetesClient;
    private static HttpServer analyzerStubServer;

    @BeforeAll
    static void setUpCluster() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker environment is required for K3s Testcontainers");
        
        k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.31.6-k3s1"));
        k3s.start();
        
        String kubeconfigYaml = k3s.getKubeConfigYaml();
        Config config = Config.fromKubeconfig(kubeconfigYaml);
        kubernetesClient = new KubernetesClientBuilder().withConfig(config).build();
        startAnalyzerStub();
    }

    @AfterAll
    static void tearDownCluster() {
        if (kubernetesClient != null) {
            kubernetesClient.close();
        }
        stopAnalyzerStub();
        if (k3s != null && k3s.isRunning()) {
            k3s.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void reconcile_scalesDeploymentOnRealK3sCluster() {
        String namespace = "payments";
        String deploymentName = "payments-api";

        kubernetesClient.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build()).createOrReplace();
        kubernetesClient.apps().deployments().inNamespace(namespace).resource(deployment(namespace, deploymentName)).create();
        waitUntilDeploymentReady(namespace, deploymentName, 1, Duration.ofMinutes(4));

        HealerProperties healerProperties = new HealerProperties();
        healerProperties.setVerificationTimeout(Duration.ofMinutes(4));
        healerProperties.setVerificationPollInterval(Duration.ofSeconds(3));

        ObservabilityProperties observabilityProperties = new ObservabilityProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        KubernetesTelemetryCollector telemetryCollector = new KubernetesTelemetryCollector(
            kubernetesClient,
            healerProperties,
            new LokiLogCollector(RestClient.builder().baseUrl("http://localhost").build(), observabilityProperties, objectMapper),
            new TempoTraceCollector(RestClient.builder().baseUrl("http://localhost").build(), observabilityProperties, objectMapper)
        );

        AnalyzerClientProperties analyzerProperties = new AnalyzerClientProperties();
        analyzerProperties.setBaseUrl("http://localhost:" + ANALYZER_PORT);
        AnalyzerRestClient analyzerClient = new AnalyzerRestClient(
            RestClient.builder().baseUrl(analyzerProperties.getBaseUrl()).build(),
            analyzerProperties,
            CircuitBreakerRegistry.ofDefaults(),
            objectMapper
        );

        HealerPolicyReconciler reconciler = new HealerPolicyReconciler(
            kubernetesClient,
            telemetryCollector,
            analyzerClient,
            new PolicyDecisionService(),
            new HealingSafetyService(healerProperties),
            new HealingRateLimiter(RateLimiterRegistry.ofDefaults()),
            new DeploymentActionExecutor(kubernetesClient),
            new DeploymentReadinessVerifier(kubernetesClient)
        );

        HealerPolicy policy = scaleOutPolicy(namespace, deploymentName);
        var result = reconciler.reconcile(policy, mock(Context.class));
        
        // Emulate the second reconciliation loop which verifies readiness
        result = reconciler.reconcile(policy, mock(Context.class));

        waitUntilDeploymentReady(namespace, deploymentName, 2, Duration.ofMinutes(4));
        Deployment updated = kubernetesClient.apps().deployments().inNamespace(namespace).withName(deploymentName).get();

        assertThat(result.isPatchStatus()).isTrue();
        assertThat(policy.getStatus()).isNotNull();
        assertThat(policy.getStatus().getPhase()).isEqualTo("HEALED");
        assertThat(policy.getStatus().getLastAction()).isEqualTo("SCALE_OUT");
        assertThat(updated.getSpec().getReplicas()).isEqualTo(2);
        assertThat(updated.getStatus().getReadyReplicas()).isGreaterThanOrEqualTo(2);
    }

    private static Deployment deployment(String namespace, String deploymentName) {
        return new DeploymentBuilder()
            .withMetadata(new ObjectMetaBuilder().withName(deploymentName).withNamespace(namespace).build())
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                    .addToMatchLabels("app", deploymentName)
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels("app", deploymentName)
                    .endMetadata()
                    .withNewSpec()
                        .addNewContainer()
                            .withName("app")
                            .withImage("nginx:1.27-alpine")
                            .addNewPort()
                                .withContainerPort(80)
                            .endPort()
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
    }

    private static HealerPolicy scaleOutPolicy(String namespace, String deploymentName) {
        HealerPolicy policy = new HealerPolicy();
        policy.setMetadata(new ObjectMetaBuilder().withName("payments-api-auto-heal").withNamespace(namespace).build());
        HealerPolicySpec spec = new HealerPolicySpec();
        spec.setTargetDeployment(deploymentName);
        spec.setAiConfidenceThreshold(0.95);

        HealingStrategySpec strategy = new HealingStrategySpec();
        strategy.setType("LatencySpike");
        strategy.setAction("SCALE_OUT");
        strategy.setMaxReplicas(3);
        spec.setStrategies(List.of(strategy));

        policy.setSpec(spec);
        return policy;
    }

    private static void waitUntilDeploymentReady(String namespace, String name, int desiredReplicas, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Deployment deployment = kubernetesClient.apps().deployments().inNamespace(namespace).withName(name).get();
            if (deployment != null
                && deployment.getStatus() != null
                && deployment.getStatus().getReadyReplicas() != null
                && deployment.getStatus().getAvailableReplicas() != null
                && deployment.getStatus().getReadyReplicas() >= desiredReplicas
                && deployment.getStatus().getAvailableReplicas() >= desiredReplicas) {
                return;
            }
            try {
                Thread.sleep(3_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Deployment wait was interrupted", ex);
            }
        }
        throw new AssertionError("Deployment " + namespace + "/" + name + " did not become ready with " + desiredReplicas + " replicas");
    }

    private static void startAnalyzerStub() throws IOException {
        analyzerStubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", ANALYZER_PORT), 0);
        analyzerStubServer.createContext("/api/v1/analyze", HealerPolicyK3sE2ETest::handleAnalyze);
        analyzerStubServer.start();
    }

    private static void handleAnalyze(HttpExchange exchange) throws IOException {
        String response = """
            {
              "diagnosis": "Deterministic e2e diagnosis: sustained latency saturation on payments-api",
              "confidence": 0.99,
              "recommended_action": {
                "type": "SCALE_OUT",
                "parameters": {}
              },
              "explanation": "E2E stub returns SCALE_OUT for stable operator validation."
            }
            """;
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response.getBytes());
        }
    }

    private static void stopAnalyzerStub() {
        if (analyzerStubServer != null) {
            analyzerStubServer.stop(0);
            analyzerStubServer = null;
        }
    }
}
