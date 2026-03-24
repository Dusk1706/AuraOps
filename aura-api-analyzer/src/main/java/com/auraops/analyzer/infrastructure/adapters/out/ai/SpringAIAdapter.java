package com.auraops.analyzer.infrastructure.adapters.out.ai;

import com.auraops.analyzer.application.ports.out.LLMProvider;
import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.ErrorCode;
import com.auraops.analyzer.domain.model.Incident;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SpringAIAdapter implements LLMProvider {

    private static final int MAX_LOG_LENGTH = 4000; // Roughly 1000 tokens
    private static final int MAX_VALIDATION_RETRIES = 2;

    private final ChatClient chatClient;
    private final AnalysisResponseParser parser;
    private final BeanOutputConverter<StructuredAnalysisResponse> outputConverter;
    private final TelemetryRedactor redactor;

    public SpringAIAdapter(
        ChatModel chatModel,
        AnalysisResponseParser parser,
        TelemetryRedactor redactor,
        ObjectMapper objectMapper
    ) {
        this.parser = parser;
        this.redactor = redactor;
        this.outputConverter = new BeanOutputConverter<>(StructuredAnalysisResponse.class, objectMapper);
        this.chatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(StructuredOutputValidationAdvisor.builder()
                .outputType(StructuredAnalysisResponse.class)
                .objectMapper(objectMapper)
                .maxRepeatAttempts(MAX_VALIDATION_RETRIES)
                .build())
            .build();
    }

    @Override
    public AnalysisResult analyze(Incident incident) {
        try {
            String redactedLogs = truncateAndRedact(incident.telemetry().logs());
            
            String promptText = """
                Analyze this Kubernetes incident and provide a deterministic Root Cause Analysis (RCA).
                
                Incident ID: {incidentId}
                Resource: {resource}
                Telemetry Logs:
                {logs}
                
                Use SUCCESS only when you can recommend a deterministic remediation action.
                Use INCONCLUSIVE when the evidence is insufficient and explain what data is missing.
                """;
            
            PromptTemplate template = new PromptTemplate(promptText);
            Prompt prompt = template.create(Map.of(
                "incidentId", incident.incidentId(),
                "resource", incident.resource().kind() + "/" + incident.resource().name(),
                "logs", redactedLogs
            ));

            StructuredAnalysisResponse structuredResponse = chatClient.prompt(prompt)
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .call()
                .entity(outputConverter);

            if (structuredResponse == null) {
                return new AnalysisResult.CriticalFailure(
                    incident.incidentId(),
                    ErrorCode.LLM_PROVIDER_ERROR,
                    "LLM provider returned no structured response"
                );
            }

            return parser.parse(incident.incidentId(), structuredResponse);
            
        } catch (Exception e) {
            return new AnalysisResult.CriticalFailure(
                incident.incidentId(),
                ErrorCode.LLM_PROVIDER_ERROR,
                "AI adapter failure [" + e.getClass().getSimpleName() + "]: " + safeMessage(e)
            );
        }
    }

    private String truncateAndRedact(List<String> logs) {
        String combined = String.join("\n", logs);
        String redacted = redactor.redact(combined);
        if (redacted.length() > MAX_LOG_LENGTH) {
            return redacted.substring(0, MAX_LOG_LENGTH) + "\n... [TRUNCATED]";
        }
        return redacted;
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "no additional details" : e.getMessage();
    }
}
