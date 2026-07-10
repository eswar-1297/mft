package com.cloudfuze.mft.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central error translation. Deliberately terse messages — we never leak stack traces,
 * SQL, or which specific field of a credential failed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(String error, String message, String timestamp) {
        static ApiError of(String message) {
            return new ApiError("error", message, Instant.now().toString());
        }
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(RemoteDirectoryMissingException.class)
    public ResponseEntity<Map<String, Object>> handleRemoteDirMissing(RemoteDirectoryMissingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "remote_directory_missing",
                "directory", ex.getDirectory(),
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, this::messageOf, (a, b) -> a));
        return ResponseEntity.badRequest().body(Map.of(
                "error", "validation_failed",
                "fields", fields,
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        // Log the full cause server-side for operators; return an opaque message to the client so
        // we never leak internals (stack traces, SQL, file paths) over the API.
        log.error("Unhandled exception serving request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("An unexpected error occurred"));
    }

    private String messageOf(FieldError fe) {
        return fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid";
    }
}
