package com.auraops.operator.infrastructure.observability;

import com.auraops.operator.config.ObservabilityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class PrometheusCollector {

    private static final Logger log = LoggerFactory.getLogger(PrometheusCollector.class);

    private final RestClient restClient;
    private final ObservabilityProperties properties;

    public PrometheusCollector(
        @Qualifier("prometheusRestClient") RestClient restClient,
        ObservabilityProperties properties
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.properties = Objects.requireNonNull(properties);
    }

    public Double getP95Latency(String serviceName, String namespace) {
        if (!properties.getPrometheus().isEnabled()) {
            return 0.0;
        }

        // Generic query for p95 latency using Istio or standard metrics
        String query = String.format(
            "histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket{app='%s',namespace='%s'}[5m])) by (le)) * 1000",
            serviceName, namespace
        );

        return queryMetric(query);
    }

    public Double getRequestRate(String serviceName, String namespace) {
        if (!properties.getPrometheus().isEnabled()) {
            return 0.0;
        }

        String query = String.format(
            "sum(rate(http_requests_total{app='%s',namespace='%s'}[5m]))",
            serviceName, namespace
        );

        return queryMetric(query);
    }

    public Double getErrorRate(String serviceName, String namespace) {
        if (!properties.getPrometheus().isEnabled()) {
            return 0.0;
        }

        String query = String.format(
            "sum(rate(http_requests_total{app='%s',namespace='%s',status=~'5..'}[5m])) / sum(rate(http_requests_total{app='%s',namespace='%s'}[5m]))",
            serviceName, namespace, serviceName, namespace
        );

        return queryMetric(query);
    }

    private Double queryMetric(String query) {
        try {
            var response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(properties.getPrometheus().getQueryPath())
                    .queryParam("query", query)
                    .build())
                .retrieve()
                .body(PrometheusResponse.class);

            if (response != null && response.data() != null && !response.data().result().isEmpty()) {
                String value = response.data().result().get(0).value().get(1).toString();
                return Double.parseDouble(value);
            }
        } catch (Exception e) {
            log.warn("Failed to query Prometheus for [{}]: {}", query, e.getMessage());
        }
        return 0.0;
    }

    private record PrometheusResponse(String status, Data data) {
        record Data(String resultType, List<Result> result) {}
        record Result(Map<String, String> metric, List<Object> value) {}
    }
}
