package com.auraops.operator.infrastructure.analyzer;

import com.auraops.operator.application.AnalyzerClient;
import com.auraops.operator.application.AnalyzerDecision;
import com.auraops.operator.application.IncidentContext;
import com.auraops.operator.config.AnalyzerClientProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class AnalyzerRestClient implements AnalyzerClient {

    private final RestClient restClient;
    private final AnalyzerClientProperties properties;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper;

    public AnalyzerRestClient(
        @Qualifier("analyzerApiRestClient")
        RestClient analyzerRestClient,
        AnalyzerClientProperties properties,
        CircuitBreakerRegistry circuitBreakerRegistry,
        ObjectMapper objectMapper
    ) {
        this.restClient = Objects.requireNonNull(analyzerRestClient);
        this.properties = Objects.requireNonNull(properties);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("analyzerClient");
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public AnalyzerDecision analyze(IncidentContext incidentContext) {
        try {
            return io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                restClient.post()
                    .uri(properties.getAnalyzePath())
                    .body(toRequest(incidentContext))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new AnalyzerProblemException(readProblem(response));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new AnalyzerProblemException(readProblem(response));
                    })
                    .body(AnalyzerSuccessResponse.class)
            ).get()
                .toDecision();
        } catch (AnalyzerProblemException ex) {
            ProblemDetail problem = ex.problemDetail();
            String errorCode = property(problem, "error_code", "ANALYZER_ERROR");
            String message = property(problem, "message", problem == null ? "Analyzer returned an error" : problem.getDetail());
            boolean requiresHuman = Boolean.parseBoolean(property(problem, "requires_human", "true"));
            if (problem != null && problem.getStatus() == 422) {
                return new AnalyzerDecision.Inconclusive(errorCode, message, requiresHuman);
            }
            return new AnalyzerDecision.Failure(errorCode, message);
        } catch (RestClientException ex) {
            return new AnalyzerDecision.Failure("ANALYZER_UNAVAILABLE", describeClientError(properties.getBaseUrl(), ex));
        } catch (Exception ex) {
            return new AnalyzerDecision.Failure(
                "ANALYZER_FAILURE",
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
            );
        }
    }

    private String describeClientError(String baseUrl, Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return "Analyzer request failed (baseUrl=" + baseUrl + ") cause=" + root.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private AnalyzerRequest toRequest(IncidentContext incidentContext) {
        return new AnalyzerRequest(
            incidentContext.incidentId(),
            new AnalyzerRequest.ResourcePayload("Deployment", incidentContext.deploymentName(), incidentContext.namespace()),
            new AnalyzerRequest.TelemetryPayload(
                incidentContext.logs(),
                new AnalyzerRequest.MetricsPayload(
                    incidentContext.memoryUsage(),
                    incidentContext.cpuUsage(),
                    incidentContext.restartCount(),
                    incidentContext.additionalMetrics()
                ),
                incidentContext.traces()
            )
        );
    }

    private String property(ProblemDetail problemDetail, String key, String fallback) {
        if (problemDetail == null || problemDetail.getProperties() == null) {
            return fallback;
        }
        Object value = problemDetail.getProperties().get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private ProblemDetail readProblem(org.springframework.http.client.ClientHttpResponse response) {
        try {
            var node = objectMapper.readTree(response.getBody());
            int status = node.path("status").asInt(500);
            ProblemDetail problemDetail = ProblemDetail.forStatus(status);
            if (node.hasNonNull("detail")) {
                problemDetail.setDetail(node.get("detail").asText());
            }
            if (node.hasNonNull("title")) {
                problemDetail.setTitle(node.get("title").asText());
            }
            if (node.hasNonNull("type")) {
                problemDetail.setType(URI.create(node.get("type").asText()));
            }
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (List.of("status", "detail", "title", "type", "instance").contains(key)) {
                    return;
                }
                problemDetail.setProperty(key, objectMapper.convertValue(entry.getValue(), Object.class));
            });
            return problemDetail;
        } catch (Exception ex) {
            ProblemDetail fallback = ProblemDetail.forStatus(500);
            fallback.setDetail("Analyzer returned an unreadable error payload");
            return fallback;
        }
    }

    private record AnalyzerRequest(
        String incident_id,
        ResourcePayload resource,
        TelemetryPayload telemetry
    ) {
        private record ResourcePayload(String kind, String name, String namespace) {}

        private record TelemetryPayload(
            List<String> logs,
            MetricsPayload metrics,
            List<String> traces
        ) {}

        private record MetricsPayload(
            String memory_usage,
            String cpu_usage,
            int restart_count,
            Map<String, Object> additional_metrics
        ) {}
    }

    private record AnalyzerSuccessResponse(
        String diagnosis,
        double confidence,
        RecommendedAction recommended_action,
        String explanation
    ) {
        private AnalyzerDecision.Success toDecision() {
            return new AnalyzerDecision.Success(
                diagnosis,
                confidence,
                recommended_action.type(),
                recommended_action.parameters(),
                explanation
            );
        }
    }

    private record RecommendedAction(String type, Map<String, Object> parameters) {}

    private static final class AnalyzerProblemException extends RuntimeException {

        private final ProblemDetail problemDetail;

        private AnalyzerProblemException(ProblemDetail problemDetail) {
            this.problemDetail = problemDetail;
        }

        private ProblemDetail problemDetail() {
            return problemDetail;
        }
    }
}
