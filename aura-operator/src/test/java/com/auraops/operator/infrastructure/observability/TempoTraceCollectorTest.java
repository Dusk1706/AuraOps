package com.auraops.operator.infrastructure.observability;

import com.auraops.operator.config.ObservabilityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TempoTraceCollectorTest {

    private MockRestServiceServer server;
    private TempoTraceCollector collector;

    @BeforeEach
    void setUp() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getTempo().setEnabled(true);
        properties.getTempo().setBaseUrl("http://localhost:3200");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        collector = new TempoTraceCollector(
            builder.baseUrl(properties.getTempo().getBaseUrl()).build(),
            properties,
            new ObjectMapper()
        );
    }

    @Test
    void collect_readsTraceSummariesFromTempoSearch() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("http://localhost:3200/api/search")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "traces": [
                    {
                      "traceID": "abc123",
                      "rootServiceName": "payments-api",
                      "rootTraceName": "POST /charge",
                      "durationMs": 845
                    }
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        List<String> traces = collector.collect(deployment());

        assertThat(traces).containsExactly("traceId=abc123 service=payments-api operation=POST /charge durationMs=845");
    }

    private Deployment deployment() {
        return new DeploymentBuilder()
            .withMetadata(new ObjectMetaBuilder().withName("payments-api").withNamespace("payments").build())
            .build();
    }
}
