package com.auraops.analyzer.infrastructure.adapters.in.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProblemDetailsFactoryTest {

    @Test
    void create_shouldIncludeTraceIdWhenPresentInMdc() {
        MDC.put("traceId", "abc123");
        try {
            ProblemDetail problemDetail = ProblemDetailsFactory.create(
                HttpStatus.BAD_REQUEST,
                "Invalid Payload",
                "INVALID_PAYLOAD",
                "Bad request",
                false,
                null
            );

            assertEquals("abc123", problemDetail.getProperties().get("trace_id"));
            assertFalse(problemDetail.getProperties().containsKey("context_link"));
        } finally {
            MDC.clear();
        }
    }
}
