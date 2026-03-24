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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

class HealerPolicyK3sE2ETest {

    private static final String CONTAINER_NAME = "auraops-k3s-e2e";
    private static final int API_PORT = 16443;
    private static final int ANALYZER_PORT = 18080;

    private static KubernetesClient kubernetesClient;
    private static Process analyzerProcess;
    private static Path analyzerLogFile;

    @BeforeAll
    static void setUpCluster() throws Exception {
        assumeTrue(commandSucceeds("docker", "version"), "Docker CLI is required for the K3s e2e test");
        startAnalyzer();

        forceRemoveContainer();
        runCommand(
            "docker", "run", "-d", "--rm",
            "--privileged",
            "-p", API_PORT + ":6443",
            "--name", CONTAINER_NAME,
            "rancher/k3s:v1.31.6-k3s1",
            "server", "--disable=traefik"
        );

        Instant deadline = Instant.now().plus(Duration.ofMinutes(4));
        Exception lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                String kubeconfig = readCommand(
                    "docker", "exec", CONTAINER_NAME, "cat", "/etc/rancher/k3s/k3s.yaml"
                );
                if (kubeconfig != null && !kubeconfig.isBlank()) {
                    kubeconfig = kubeconfig.replace("https://127.0.0.1:6443", "https://127.0.0.1:" + API_PORT);
                    Config config = Config.fromKubeconfig(kubeconfig);
                    kubernetesClient = new KubernetesClientBuilder().withConfig(config).build();
                    kubernetesClient.namespaces().list();
                    return;
                }
            } catch (Exception ex) {
                lastFailure = ex;
                Thread.sleep(3_000L);
            }
        }

        forceRemoveContainer();
        throw new IllegalStateException("K3s cluster did not become reachable", lastFailure);
    }

    @AfterAll
    static void tearDownCluster() {
        if (kubernetesClient != null) {
            kubernetesClient.close();
        }
        stopAnalyzer();
        forceRemoveContainer();
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
            new DeploymentReadinessVerifier(kubernetesClient, healerProperties)
        );

        HealerPolicy policy = scaleOutPolicy(namespace, deploymentName);
        var result = reconciler.reconcile(policy, mock(Context.class));

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

    private static void startAnalyzer() throws Exception {
        Path analyzerDir = Path.of("..", "aura-api-analyzer").toAbsolutePath().normalize();
        assumeTrue(Files.exists(analyzerDir.resolve("gradlew.bat")) || Files.exists(analyzerDir.resolve("gradlew")),
            "Analyzer project is required for the full-stack e2e test");

        if (Files.exists(analyzerDir.resolve("gradlew.bat"))) {
            runCommand(analyzerDir, "cmd.exe", "/c", "gradlew.bat", "bootJar", "-x", "test");
        } else {
            runCommand(analyzerDir, "gradlew", "bootJar", "-x", "test");
        }

        Path jar = Files.list(analyzerDir.resolve("build").resolve("libs"))
            .filter(path -> path.getFileName().toString().endsWith(".jar"))
            .filter(path -> !path.getFileName().toString().contains("-plain"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Analyzer boot jar was not generated"));

        analyzerLogFile = Files.createTempFile("aura-api-analyzer-e2e", ".log");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        analyzerProcess = new ProcessBuilder(
            javaExecutable,
            "-jar",
            jar.toString(),
            "--spring.profiles.active=e2e",
            "--server.port=" + ANALYZER_PORT
        )
            .directory(analyzerDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(analyzerLogFile.toFile())
            .start();

        waitForAnalyzerHealth();
    }

    private static void waitForAnalyzerHealth() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + ANALYZER_PORT + "/actuator/health"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        Exception lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                if (analyzerProcess != null && !analyzerProcess.isAlive()) {
                    String logs = analyzerLogFile == null || !Files.exists(analyzerLogFile)
                        ? ""
                        : Files.readString(analyzerLogFile);
                    throw new IllegalStateException("Analyzer process exited before becoming healthy" + System.lineSeparator() + logs);
                }

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"")) {
                    return;
                }
            } catch (Exception ex) {
                lastFailure = ex;
                Thread.sleep(2_000L);
            }
        }

        String logs = analyzerLogFile == null || !Files.exists(analyzerLogFile)
            ? ""
            : Files.readString(analyzerLogFile);
        throw new IllegalStateException("Analyzer process did not become healthy" + System.lineSeparator() + logs, lastFailure);
    }

    private static boolean commandSucceeds(String... command) {
        try {
            runCommand(Path.of(".").toAbsolutePath().normalize(), command);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String readCommand(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + System.lineSeparator() + output);
        }
        return output;
    }

    private static void runCommand(String... command) throws IOException, InterruptedException {
        runCommand(Path.of(".").toAbsolutePath().normalize(), command);
    }

    private static void runCommand(Path workingDirectory, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + System.lineSeparator() + output);
        }
    }

    private static void stopAnalyzer() {
        if (analyzerProcess == null) {
            return;
        }
        analyzerProcess.destroy();
        try {
            if (!analyzerProcess.waitFor(10, TimeUnit.SECONDS)) {
                analyzerProcess.destroyForcibly();
                analyzerProcess.waitFor(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            analyzerProcess.destroyForcibly();
        }
    }

    private static void forceRemoveContainer() {
        try {
            runCommand("docker", "rm", "-f", CONTAINER_NAME);
        } catch (Exception ignored) {
        }
    }
}
