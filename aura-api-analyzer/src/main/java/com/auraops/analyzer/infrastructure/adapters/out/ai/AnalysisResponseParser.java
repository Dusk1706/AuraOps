package com.auraops.analyzer.infrastructure.adapters.out.ai;

import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.ErrorCode;
import com.auraops.analyzer.domain.model.RemediationAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AnalysisResponseParser {

    private final ObjectMapper objectMapper;

    public AnalysisResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisResult parse(String incidentId, String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            String status = Optional.ofNullable(field(root, "status")).map(JsonNode::asText).orElse("INCONCLUSIVE");

            if ("SUCCESS".equalsIgnoreCase(status)) {
                return parseSuccess(incidentId, root);
            }

            return parseInconclusive(incidentId, root);
        } catch (Exception e) {
            return new AnalysisResult.CriticalFailure(
                incidentId,
                ErrorCode.PARSING_ERROR,
                "Failed to parse LLM response: " + e.getMessage()
            );
        }
    }

    public AnalysisResult parse(String incidentId, StructuredAnalysisResponse response) {
        try {
            String status = Optional.ofNullable(response.status()).orElse("INCONCLUSIVE");
            if ("SUCCESS".equalsIgnoreCase(status)) {
                return parseStructuredSuccess(incidentId, response);
            }

            return new AnalysisResult.Inconclusive(
                incidentId,
                Optional.ofNullable(response.reason()).filter(reason -> !reason.isBlank()).orElse("Unknown reason"),
                response.missingDataPoints() == null ? List.of() : response.missingDataPoints()
            );
        } catch (Exception e) {
            return new AnalysisResult.CriticalFailure(
                incidentId,
                ErrorCode.PARSING_ERROR,
                "Failed to map structured LLM response: " + e.getMessage()
            );
        }
    }

    private AnalysisResult parseSuccess(String incidentId, JsonNode root) {
        String diagnosis = textValue(root, "diagnosis");
        Double confidence = doubleValue(root, "confidence");
        String technicalReasoning = firstNonBlankText(root, "technicalReasoning", "technical_reasoning", "explanation");
        JsonNode actionNode = firstPresentField(root, "recommendedAction", "recommended_action");

        List<String> missing = new ArrayList<>();
        if (diagnosis.isBlank()) {
            missing.add("diagnosis");
        }
        if (confidence == null || confidence <= 0.0 || confidence > 1.0) {
            missing.add("confidence");
        }
        if (technicalReasoning.isBlank()) {
            missing.add("technicalReasoning");
        }
        if (actionNode == null || actionNode.isNull()) {
            missing.add("recommendedAction");
        }

        if (!missing.isEmpty()) {
            return new AnalysisResult.Inconclusive(
                incidentId,
                "LLM response missing mandatory fields for SUCCESS",
                missing
            );
        }

        String actionType = Optional.ofNullable(field(actionNode, "type")).map(JsonNode::asText).orElse("");
        if (actionType.isBlank()) {
            return new AnalysisResult.Inconclusive(
                incidentId,
                "LLM response missing mandatory fields for SUCCESS",
                List.of("recommendedAction.type")
            );
        }

        Map<String, Object> parameters = Optional.ofNullable(actionNode.get("parameters"))
            .filter(node -> !node.isNull())
            .map(node -> objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {}))
            .orElse(Map.of());

        return new AnalysisResult.Success(
            incidentId,
            diagnosis,
            confidence,
            new RemediationAction(actionType, parameters),
            technicalReasoning
        );
    }

    private AnalysisResult parseStructuredSuccess(String incidentId, StructuredAnalysisResponse response) {
        String diagnosis = Optional.ofNullable(response.diagnosis()).orElse("");
        Double confidence = response.confidence();
        String technicalReasoning = Optional.ofNullable(response.technicalReasoning()).orElse("");
        StructuredAnalysisResponse.StructuredRemediationAction action = response.recommendedAction();

        List<String> missing = new ArrayList<>();
        if (diagnosis.isBlank()) {
            missing.add("diagnosis");
        }
        if (confidence == null || confidence <= 0.0 || confidence > 1.0) {
            missing.add("confidence");
        }
        if (technicalReasoning.isBlank()) {
            missing.add("technicalReasoning");
        }
        if (action == null) {
            missing.add("recommendedAction");
        }

        if (!missing.isEmpty()) {
            return new AnalysisResult.Inconclusive(
                incidentId,
                "LLM response missing mandatory fields for SUCCESS",
                missing
            );
        }

        String actionType = Optional.ofNullable(action.type()).orElse("");
        if (actionType.isBlank()) {
            return new AnalysisResult.Inconclusive(
                incidentId,
                "LLM response missing mandatory fields for SUCCESS",
                List.of("recommendedAction.type")
            );
        }

        return new AnalysisResult.Success(
            incidentId,
            diagnosis,
            confidence,
            new RemediationAction(actionType, action.parameters() == null ? Map.of() : action.parameters()),
            technicalReasoning
        );
    }

    private AnalysisResult parseInconclusive(String incidentId, JsonNode root) {
        String reason = Optional.ofNullable(field(root, "reason")).map(JsonNode::asText).orElse("Unknown reason");
        JsonNode missingNode = firstPresentField(root, "missingDataPoints", "missing_data_points");
        List<String> missingDataPoints = missingNode == null || missingNode.isNull()
            ? List.of()
            : objectMapper.convertValue(missingNode, new TypeReference<List<String>>() {});

        return new AnalysisResult.Inconclusive(
            incidentId,
            reason,
            missingDataPoints == null ? List.of() : missingDataPoints
        );
    }

    private JsonNode firstPresentField(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode candidate = field(node, name);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private JsonNode field(JsonNode node, String name) {
        JsonNode candidate = node.get(name);
        return candidate == null || candidate.isMissingNode() ? null : candidate;
    }

    private String textValue(JsonNode node, String name) {
        return Optional.ofNullable(field(node, name)).map(JsonNode::asText).orElse("");
    }

    private Double doubleValue(JsonNode node, String name) {
        JsonNode value = field(node, name);
        if (value == null || !value.isNumber()) {
            return null;
        }
        return value.asDouble();
    }

    private String firstNonBlankText(JsonNode node, String... names) {
        for (String name : names) {
            String value = textValue(node, name);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
