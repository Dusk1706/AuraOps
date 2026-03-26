package com.auraops.analyzer.infrastructure.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@Profile({"openai", "anthropic"})
public class OpenAiEmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {
        
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
        return new OpenAiEmbeddingModel(openAiApi, org.springframework.ai.document.MetadataMode.EMBED, 
                OpenAiEmbeddingOptions.builder().build());
    }
}
