package com.auraops.operator.infrastructure.observability;

import com.auraops.operator.config.ObservabilityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class LokiLogCollector {

    private final RestClient restClient;
    private final ObservabilityProperties properties;
    private final ObjectMapper objectMapper;

    public LokiLogCollector(
        @Qualifier("lokiRestClient") RestClient restClient,
        ObservabilityProperties properties,
        ObjectMapper objectMapper
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.properties = Objects.requireNonNull(properties);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public List<String> collect(Deployment deployment, int maxLines) {
        return collectWithStatus(deployment, maxLines).logs();
    }

    public Result collectWithStatus(Deployment deployment, int maxLines) {
        if (!properties.getLoki().isEnabled()) {
            return Result.disabled();
        }

        String namespace = deployment.getMetadata().getNamespace();
        String deploymentName = deployment.getMetadata().getName();
        String query = "{namespace=\"" + namespace + "\", " + properties.getLoki().getLabelName() + "=\"" + deploymentName + "\"}";
        Instant end = Instant.now();
        Instant start = end.minus(properties.getLoki().getLookback());

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String uri = properties.getLoki().getQueryRangePath()
                + "?query=" + encodedQuery
                + "&limit=" + maxLines
                + "&direction=backward"
                + "&start=" + (start.toEpochMilli() * 1_000_000L)
                + "&end=" + (end.toEpochMilli() * 1_000_000L);
            String body = restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
            return Result.available(parse(body, maxLines));
        } catch (RestClientException ex) {
            return Result.unavailable("LOKI_UNAVAILABLE", describeClientError(properties.getLoki().getBaseUrl(), ex));
        } catch (RuntimeException ex) {
            return Result.unavailable("LOKI_PARSE_ERROR", "Failed to parse Loki response: " + ex.getMessage());
        }
    }

    private String describeClientError(String baseUrl, Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return "Loki request failed (baseUrl=" + baseUrl + ") cause=" + root.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private List<String> parse(String body, int maxLines) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            List<String> logs = new ArrayList<>();
            for (JsonNode stream : root.path("data").path("result")) {
                for (JsonNode value : stream.path("values")) {
                    if (value.isArray() && value.size() >= 2) {
                        logs.add(value.get(1).asText());
                        if (logs.size() >= maxLines) {
                            return logs;
                        }
                    }
                }
            }
            return logs;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public record Result(List<String> logs, boolean enabled, boolean available, String reasonCode, String reason) {

        static Result disabled() {
            return new Result(List.of(), false, true, null, null);
        }

        static Result available(List<String> logs) {
            return new Result(logs == null ? List.of() : logs, true, true, null, null);
        }

        static Result unavailable(String reasonCode, String reason) {
            return new Result(List.of(), true, false, reasonCode, reason);
        }
    }
}
