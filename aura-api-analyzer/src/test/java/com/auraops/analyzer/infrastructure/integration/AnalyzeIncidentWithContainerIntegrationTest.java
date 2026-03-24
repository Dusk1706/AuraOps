package com.auraops.analyzer.infrastructure.integration;

import com.auraops.analyzer.application.ports.out.LLMProvider;
import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.Incident;
import com.auraops.analyzer.domain.model.RemediationAction;
import com.auraops.analyzer.infrastructure.adapters.in.web.AnalysisResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AnalyzeIncidentWithContainerIntegrationTest {

    @Container
    static final GenericContainer<?> failingPod = new GenericContainer<>(DockerImageName.parse("alpine:3.20"))
        .withCommand(
            "sh",
            "-c",
            "printf 'java.lang.OutOfMemoryError: Java heap space\nCrashLoopBackOff\nBack-off restarting failed container\n'; tail -f /dev/null"
        );

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private LLMProvider llmProvider;

    @Test
    void analyze_withContainerBackedTelemetry_returnsDeterministicAction() {
        List<String> logs = failingPod.getLogs()
            .lines()
            .filter(line -> !line.isBlank())
            .toList();

        assertThat(logs).isNotEmpty();

        when(llmProvider.analyze(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0, Incident.class);

            assertThat(incident.telemetry().logs()).containsAll(logs);
            assertThat(incident.telemetry().logs()).anySatisfy(line -> assertThat(line).contains("OutOfMemoryError"));

            return new AnalysisResult.Success(
                incident.incidentId(),
                "Pod crashed due to Java heap exhaustion in container " + shortContainerId(),
                0.98,
                new RemediationAction(
                    "ROLLING_RESTART",
                    Map.of(
                        "resource", incident.resource().kind() + "/" + incident.resource().name(),
                        "namespace", incident.resource().namespace(),
                        "container_id", shortContainerId()
                    )
                ),
                "Container logs show an OutOfMemoryError followed by CrashLoopBackOff, which is sufficient for a deterministic restart action."
            );
        });

        Map<String, Object> request = Map.of(
            "incident_id", "integration-oom-001",
            "resource", Map.of(
                "kind", "Pod",
                "name", "payments-api-7d8f6f7d9b-zk2qr",
                "namespace", "payments"
            ),
            "telemetry", Map.of(
                "logs", logs,
                "metrics", Map.of(
                    "memory_usage", "970Mi",
                    "cpu_usage", "0.85",
                    "restart_count", 4,
                    "additional_metrics", Map.of(
                        "container_id", shortContainerId(),
                        "cluster", "testcontainers"
                    )
                ),
                "traces", List.of("trace-" + shortContainerId())
            )
        );

        ResponseEntity<AnalysisResponseDto> response = restTemplate.postForEntity(
            "/api/v1/analyze",
            request,
            AnalysisResponseDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().diagnosis()).contains("Java heap exhaustion");
        assertThat(response.getBody().recommendedAction().type()).isEqualTo("ROLLING_RESTART");
        assertThat(response.getBody().recommendedAction().parameters())
            .containsEntry("namespace", "payments")
            .containsEntry("container_id", shortContainerId());
        assertThat(response.getBody().explanation()).contains("CrashLoopBackOff");
    }

    private static String shortContainerId() {
        return failingPod.getContainerId().substring(0, 12);
    }
}
