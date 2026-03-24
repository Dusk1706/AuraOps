package com.auraops.analyzer.infrastructure.smoke;

import com.auraops.analyzer.AuraAnalyzerApplication;
import com.auraops.analyzer.application.ports.out.LLMProvider;
import com.auraops.analyzer.infrastructure.adapters.out.ai.SpringAIAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderConfigurationSmokeTest {

    @Test
    void contextLoadsWithOpenAiSelected() {
        assertContextLoadsFor(
            "openai",
            "open",
            "spring.ai.openai.api-key=test-key",
            "spring.ai.openai.chat.api-key=test-key",
            "spring.ai.openai.base-url=http://localhost:65535",
            "spring.ai.openai.chat.options.model=gpt-4o-mini"
        );
    }

    @Test
    void contextLoadsWithAnthropicSelected() {
        assertContextLoadsFor(
            "anthropic",
            "anthropic",
            "spring.ai.anthropic.api-key=test-key",
            "spring.ai.anthropic.base-url=http://localhost:65535",
            "spring.ai.anthropic.chat.options.model=claude-3-5-sonnet-latest"
        );
    }

    @Test
    void contextLoadsWithOllamaSelected() {
        assertContextLoadsFor(
            "ollama",
            "ollama",
            "spring.ai.ollama.base-url=http://localhost:11434",
            "spring.ai.ollama.chat.options.model=llama3.1",
            "spring.ai.ollama.init.pull-model-strategy=never"
        );
    }

    private void assertContextLoadsFor(String provider, String beanNameFragment, String... extraProperties) {
        List<String> properties = new ArrayList<>(List.of(
            "spring.main.web-application-type=none",
            "spring.profiles.active=" + provider,
            "management.endpoints.enabled-by-default=false"
        ));
        Collections.addAll(properties, extraProperties);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AuraAnalyzerApplication.class)
            .properties(properties.toArray(String[]::new))
            .run()) {

            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly(provider);
            assertThat(context.getEnvironment().getProperty("spring.ai.model.chat")).isEqualTo(provider);
            assertThat(context.getBean(LLMProvider.class)).isInstanceOf(SpringAIAdapter.class);

            List<String> chatModelBeanNames = Arrays.asList(context.getBeanNamesForType(ChatModel.class));
            assertThat(chatModelBeanNames).singleElement().satisfies(name -> assertThat(name).containsIgnoringCase(beanNameFragment));
            assertThat(context.getBean(ChatModel.class)).isNotNull();
        }
    }
}
