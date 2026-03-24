package com.auraops.analyzer.infrastructure.adapters.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.slf4j.MDC;

import java.net.URI;
import java.util.Optional;

final class ProblemDetailsFactory {

    private ProblemDetailsFactory() {
    }

    static ProblemDetail create(
        HttpStatus status,
        String title,
        String errorCode,
        String detail,
        boolean requiresHuman,
        String contextLink
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create("https://auraops.dev/problems/" + errorCode.toLowerCase()));
        problemDetail.setProperty("error_code", errorCode);
        problemDetail.setProperty("message", detail);
        problemDetail.setProperty("requires_human", requiresHuman);
        Optional.ofNullable(traceId()).ifPresent(traceId -> problemDetail.setProperty("trace_id", traceId));
        if (contextLink != null && !contextLink.isBlank()) {
            problemDetail.setProperty("context_link", contextLink);
        }
        return problemDetail;
    }

    private static String traceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("trace_id");
        }
        return traceId == null || traceId.isBlank() ? null : traceId;
    }
}
