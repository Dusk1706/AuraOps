package com.auraops.operator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final List<String> allowedOriginPatterns;

    public WebSocketConfig(@Value("${auraops.websocket.allowed-origin-patterns}") String allowedOriginPatternsCsv) {
        this.allowedOriginPatterns = Arrays.stream(allowedOriginPatternsCsv.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();

        if (allowedOriginPatterns.isEmpty()) {
            throw new IllegalStateException("auraops.websocket.allowed-origin-patterns must define at least one origin");
        }
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-native")
            .setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
