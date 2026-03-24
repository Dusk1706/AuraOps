package com.auraops.analyzer.infrastructure.adapters.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));

        return ProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "Invalid Payload",
            "INVALID_PAYLOAD",
            message,
            false,
            null
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "Domain Violation",
            "DOMAIN_VIOLATION",
            ex.getMessage(),
            false,
            null
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadablePayload(HttpMessageNotReadableException ex) {
        return ProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "Invalid Payload",
            "INVALID_PAYLOAD",
            "Request body is malformed or has invalid field types",
            false,
            null
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        return ProblemDetailsFactory.create(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Unexpected Error",
            "UNEXPECTED_ERROR",
            "Unexpected server error: " + ex.getClass().getSimpleName(),
            true,
            null
        );
    }
}
