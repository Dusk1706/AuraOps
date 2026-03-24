package com.auraops.operator.e2e;

import com.auraops.operator.config.ObservabilityProperties;
import com.auraops.operator.infrastructure.observability.LokiLogCollector;
import com.auraops.operator.infrastructure.observability.TempoTraceCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
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
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ObservabilityApisE2ETest {

    private static final String LOKI_CONTAINER = "auraops-loki-e2e";
    private static final String TEMPO_CONTAINER = "auraops-tempo-e2e";
    private static final int LOKI_PORT = 13100;
    private static final int TEMPO_PORT = 13200;

    @BeforeAll
    static void startStack() throws Exception {
        assumeTrue(commandSucceeds("docker", "version"), "Docker CLI is required for observability e2e tests");

        Path rootDir = Path.of(".").toAbsolutePath().normalize();
        Path lokiConfig = rootDir.resolve("..").resolve("infra").resolve("observability").resolve("loki").resolve("loki-config.yaml").normalize();
        Path tempoConfig = rootDir.resolve("..").resolve("infra").resolve("observability").resolve("tempo").resolve("tempo-config.yaml").normalize();

        forceRemoveContainer(LOKI_CONTAINER);
        forceRemoveContainer(TEMPO_CONTAINER);

        runCommand(
            rootDir,
            "docker", "run", "-d", "--rm",
            "-p", LOKI_PORT + ":3100",
            "--name", LOKI_CONTAINER,
            "-v", lokiConfig + ":/etc/loki/local-config.yaml:ro",
            "grafana/loki:3.4.2",
            "--config.file=/etc/loki/local-config.yaml"
        );
        runCommand(
            rootDir,
            "docker", "run", "-d", "--rm",
            "-p", TEMPO_PORT + ":3200",
            "--name", TEMPO_CONTAINER,
            "-v", tempoConfig + ":/etc/tempo/tempo-config.yaml:ro",
            "grafana/tempo:2.7.2",
            "--config.file=/etc/tempo/tempo-config.yaml"
        );

        waitForHttpOk("http://localhost:" + LOKI_PORT + "/loki/api/v1/labels");
        waitForHttpOk("http://localhost:" + TEMPO_PORT + "/api/search?limit=1");
    }

    @AfterAll
    static void stopStack() {
        forceRemoveContainer(LOKI_CONTAINER);
        forceRemoveContainer(TEMPO_CONTAINER);
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void collectors_canTalkToLiveLokiAndTempoApis() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getLoki().setEnabled(true);
        properties.getLoki().setBaseUrl("http://localhost:" + LOKI_PORT);
        properties.getTempo().setEnabled(true);
        properties.getTempo().setBaseUrl("http://localhost:" + TEMPO_PORT);

        ObjectMapper objectMapper = new ObjectMapper();
        LokiLogCollector lokiLogCollector = new LokiLogCollector(
            RestClient.builder().baseUrl(properties.getLoki().getBaseUrl()).build(),
            properties,
            objectMapper
        );
        TempoTraceCollector tempoTraceCollector = new TempoTraceCollector(
            RestClient.builder().baseUrl(properties.getTempo().getBaseUrl()).build(),
            properties,
            objectMapper
        );

        Deployment deployment = new DeploymentBuilder()
            .withMetadata(new ObjectMetaBuilder().withName("payments-api").withNamespace("payments").build())
            .build();

        List<String> logs = lokiLogCollector.collect(deployment, 10);
        List<String> traces = tempoTraceCollector.collect(deployment);

        assertThat(logs).isEmpty();
        assertThat(traces).isEmpty();
    }

    private static void waitForHttpOk(String url) throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        Exception lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception ex) {
                lastFailure = ex;
            }
            Thread.sleep(2_000L);
        }

        throw new IllegalStateException("HTTP endpoint did not become ready: " + url, lastFailure);
    }

    private static boolean commandSucceeds(String... command) {
        try {
            runCommand(Path.of(".").toAbsolutePath().normalize(), command);
            return true;
        } catch (Exception ex) {
            return false;
        }
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

    private static void forceRemoveContainer(String containerName) {
        try {
            runCommand(Path.of(".").toAbsolutePath().normalize(), "docker", "rm", "-f", containerName);
        } catch (Exception ignored) {
        }
    }
}
