package com.auraops.analyzer.infrastructure.adapters.in.web;

import com.auraops.analyzer.application.ports.in.AnalyzeIncidentUseCase;
import com.auraops.analyzer.domain.model.AnalysisResult;
import com.auraops.analyzer.domain.model.Incident;
import com.auraops.analyzer.domain.model.Metrics;
import com.auraops.analyzer.domain.model.Resource;
import com.auraops.analyzer.domain.model.Telemetry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final AnalyzeIncidentUseCase analyzeIncidentUseCase;

    public AnalysisController(AnalyzeIncidentUseCase analyzeIncidentUseCase) {
        this.analyzeIncidentUseCase = Objects.requireNonNull(analyzeIncidentUseCase);
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@Valid @RequestBody AnalysisRequestDto requestDto) {
        Incident incident = mapToDomain(requestDto);
        AnalysisResult result = analyzeIncidentUseCase.execute(incident);
        return mapToResponse(result);
    }

    private Incident mapToDomain(AnalysisRequestDto dto) {
        return new Incident(
            dto.incidentId(),
            new Resource(
                dto.resource().kind(),
                dto.resource().name(),
                dto.resource().namespace()
            ),
            new Telemetry(
                dto.telemetry().logs(),
                new Metrics(
                    dto.telemetry().metrics().memoryUsage(),
                    dto.telemetry().metrics().cpuUsage(),
                    dto.telemetry().metrics().restartCount(),
                    Optional.ofNullable(dto.telemetry().metrics().additionalMetrics()).orElse(java.util.Map.of())
                ),
                Optional.ofNullable(dto.telemetry().traces()).orElse(java.util.List.of())
            )
        );
    }

    private ResponseEntity<?> mapToResponse(AnalysisResult result) {
        return switch (result) {
            case AnalysisResult.Success s -> ResponseEntity.ok(
                new AnalysisResponseDto(
                    s.diagnosis(),
                    s.confidence(),
                    new AnalysisResponseDto.RemediationActionDto(
                        s.recommendedAction().type(),
                        s.recommendedAction().parameters()
                    ),
                    s.technicalReasoning()
                )
            );
            case AnalysisResult.Inconclusive i -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(problemDetail(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Analysis Inconclusive",
                    com.auraops.analyzer.domain.model.ErrorCode.AI_LOW_CONFIDENCE.name(),
                    i.reason() + (i.missingDataPoints().isEmpty() ? "" : ". Missing: " + String.join(", ", i.missingDataPoints())),
                    true
                ));
            case AnalysisResult.CriticalFailure f -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problemDetail(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Critical Analysis Failure",
                    f.errorCode().name(),
                    f.message(),
                    true
                ));
        };
    }

    private ProblemDetail problemDetail(
        HttpStatus status,
        String title,
        String errorCode,
        String detail,
        boolean requiresHuman
    ) {
        return ProblemDetailsFactory.create(status, title, errorCode, detail, requiresHuman, null);
    }
}
