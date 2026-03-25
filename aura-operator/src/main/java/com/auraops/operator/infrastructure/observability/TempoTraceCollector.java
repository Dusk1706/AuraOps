package com.auraops.operator.infrastructure.observability;

import com.auraops.operator.config.ObservabilityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class TempoTraceCollector {

    private final RestClient restClient;
    private final ObservabilityProperties properties;
    private final ObjectMapper objectMapper;

    public TempoTraceCollector(
        @Qualifier("tempoRestClient") RestClient restClient,
        ObservabilityProperties properties,
        ObjectMapper objectMapper
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.properties = Objects.requireNonNull(properties);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public List<String> collect(Deployment deployment) {
        return collectWithStatus(deployment).traces();
    }

    public Result collectWithStatus(Deployment deployment) {
        if (!properties.getTempo().isEnabled()) {
            return Result.disabled();
        }

        String namespace = deployment.getMetadata().getNamespace();
        String deploymentName = deployment.getMetadata().getName();
        Instant start = Instant.now().minus(properties.getTempo().getLookback());

        try {
            String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(properties.getTempo().getSearchPath())
                    .queryParam("start", start.getEpochSecond())
                    .queryParam("limit", properties.getTempo().getLimit())
                    .queryParam("tags", "resource.namespace.name=" + namespace + " service.name=" + deploymentName)
                    .build())
                .retrieve()
                .body(String.class);
            return Result.available(parse(body, properties.getTempo().getLimit()));
        } catch (RestClientException ex) {
            return Result.unavailable("TEMPO_UNAVAILABLE", describeClientError(properties.getTempo().getBaseUrl(), ex));
        } catch (RuntimeException ex) {
            return Result.unavailable("TEMPO_PARSE_ERROR", "Failed to parse Tempo response: " + ex.getMessage());
        }
    }

    private String describeClientError(String baseUrl, Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return "Tempo request failed (baseUrl=" + baseUrl + ") cause=" + root.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private List<String> parse(String body, int limit) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode traces = root.has("traces") ? root.get("traces") : root.path("results");
            if (!traces.isArray()) {
                return List.of();
            }

            List<String> summaries = new ArrayList<>();
            for (JsonNode trace : traces) {
                String traceId = text(trace, "traceID", text(trace, "traceId", "unknown"));
                String rootService = text(trace, "rootServiceName", text(trace, "serviceName", "unknown"));
                String rootTrace = text(trace, "rootTraceName", text(trace, "name", "unknown"));
                long durationMs = trace.path("durationMs").asLong(trace.path("durationNano").asLong(0L) / 1_000_000L);
                summaries.add("traceId=" + traceId + " service=" + rootService + " operation=" + rootTrace + " durationMs=" + durationMs);
                if (summaries.size() >= limit) {
                    break;
                }
            }
            return summaries;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    public record Result(List<String> traces, boolean enabled, boolean available, String reasonCode, String reason) {

        static Result disabled() {
            return new Result(List.of(), false, true, null, null);
        }

        static Result available(List<String> traces) {
            return new Result(traces == null ? List.of() : traces, true, true, null, null);
        }

        static Result unavailable(String reasonCode, String reason) {
            return new Result(List.of(), true, false, reasonCode, reason);
        }
    }
}
