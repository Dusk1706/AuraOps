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

class LokiLogCollectorTest {

    private MockRestServiceServer server;
    private LokiLogCollector collector;

    @BeforeEach
    void setUp() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getLoki().setEnabled(true);
        properties.getLoki().setBaseUrl("http://localhost:3100");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        collector = new LokiLogCollector(
            builder.baseUrl(properties.getLoki().getBaseUrl()).build(),
            properties,
            new ObjectMapper()
        );
    }

    @Test
    void collect_readsLogLinesFromLokiQueryRange() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("http://localhost:3100/loki/api/v1/query_range")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "status": "success",
                  "data": {
                    "resultType": "streams",
                    "result": [
                      {
                        "stream": {"app":"payments-api"},
                        "values": [
                          ["1711260000000000001", "first line"],
                          ["1711260000000000002", "second line"]
                        ]
                      }
                    ]
                  }
                }
                """, MediaType.APPLICATION_JSON));

        List<String> logs = collector.collect(deployment(), 10);

        assertThat(logs).containsExactly("first line", "second line");
    }

    private Deployment deployment() {
        return new DeploymentBuilder()
            .withMetadata(new ObjectMetaBuilder().withName("payments-api").withNamespace("payments").build())
            .build();
    }
}
