package com.CareerNexus_Backend.CareerNexus.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(Exception ex) {
        return ResponseEntity.badRequest().body(buildError("Check the required fields and try again.", 400));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(buildError("Send this request as JSON or multipart form data.", 415));
    }

    // Handles ResourceNotFoundException (404)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {
        logger.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildError(ex.getMessage(), 404));
    }

    // Handles UnauthorizedActionException (403)
    @ExceptionHandler(UnauthorizedActionException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedActionException(
            UnauthorizedActionException ex) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(buildError(ex.getMessage(), 403));
    }

    // Handles InvalidFileException (400)
    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFileException(
            InvalidFileException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(ex.getMessage(), 400));
    }

    // Handles DuplicateUserException (409)
    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateUserException(
            DuplicateUserException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildError(ex.getMessage(), 409));
    }

    // Handles file size exceeding limit (400)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError("File size exceeds the 10MB limit", 400));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(buildError(ex.getMessage(), 400));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildError("You do not have access to this action.", 403));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(buildError("This record already exists or is still in use.", 409));
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleUnavailableService(ExternalServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(buildError(ex.getMessage(), 503));
    }

    // Catch-all for any other unexpected errors (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception occurred: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("Something went wrong. Please try again.", 500));
    }

    // Helper to build consistent error response body
    private Map<String, Object> buildError(String message, int status) {
        return Map.of(
                "error", message,
                "status", status,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
