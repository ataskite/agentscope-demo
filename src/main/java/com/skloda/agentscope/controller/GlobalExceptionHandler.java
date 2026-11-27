package com.skloda.agentscope.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * P3-1: unified REST error mapping.
 *
 * <p>Previously controllers returned raw {@code e.getMessage()} to clients — internal
 * exception text can leak class names, file paths, and driver details. Server-side
 * details now stay in the log; clients receive sanitized business messages.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Validation errors thrown by our own guards carry intentionally safe messages. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "code", "BAD_REQUEST"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleInternal(Exception e) {
        // Internal detail (class names, paths, driver text) stays in the server log only
        log.error("Unhandled REST exception", e);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal server error",
                "code", "INTERNAL_ERROR"));
    }
}
