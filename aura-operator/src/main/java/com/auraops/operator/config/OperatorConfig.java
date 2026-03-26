package com.auraops.operator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties({AnalyzerClientProperties.class, HealerProperties.class, ObservabilityProperties.class})
public class OperatorConfig {

    @Bean
    @Qualifier("analyzerApiRestClient")
    RestClient analyzerApiRestClient(RestClient.Builder builder, AnalyzerClientProperties properties) {
        JdkClientHttpRequestFactory requestFactory = requestFactory(properties.getConnectTimeout(), properties.getReadTimeout());
        return builder
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .build();
    }

    @Bean
    @Qualifier("lokiRestClient")
    RestClient lokiRestClient(RestClient.Builder builder, ObservabilityProperties properties) {
        JdkClientHttpRequestFactory requestFactory = requestFactory(
            properties.getLoki().getConnectTimeout(),
            properties.getLoki().getReadTimeout()
        );
        return builder
            .baseUrl(properties.getLoki().getBaseUrl())
            .requestFactory(requestFactory)
            .build();
    }

    @Bean
    @Qualifier("tempoRestClient")
    RestClient tempoRestClient(RestClient.Builder builder, ObservabilityProperties properties) {
        JdkClientHttpRequestFactory requestFactory = requestFactory(
            properties.getTempo().getConnectTimeout(),
            properties.getTempo().getReadTimeout()
        );
        return builder
            .baseUrl(properties.getTempo().getBaseUrl())
            .requestFactory(requestFactory)
            .build();
    }

    @Bean
    @Qualifier("prometheusRestClient")
    RestClient prometheusRestClient(RestClient.Builder builder, ObservabilityProperties properties) {
        JdkClientHttpRequestFactory requestFactory = requestFactory(
            properties.getPrometheus().getConnectTimeout(),
            properties.getPrometheus().getReadTimeout()
        );
        return builder
            .baseUrl(properties.getPrometheus().getBaseUrl())
            .requestFactory(requestFactory)
            .build();
    }

    private JdkClientHttpRequestFactory requestFactory(java.time.Duration connectTimeout, java.time.Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
