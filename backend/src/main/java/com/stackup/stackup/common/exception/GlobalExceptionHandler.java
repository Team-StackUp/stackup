package com.stackup.stackup.common.exception;

import com.stackup.stackup.common.response.ApiErrorResponse;
import com.stackup.stackup.common.trace.TraceContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException exception) {
        ApiErrorCode errorCode = exception.getErrorCode();
        return buildResponse(errorCode, resolveMessage(exception, errorCode), exception.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<Map<String, Object>> errors = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::toFieldErrorDetail)
            .toList();

        return buildResponse(ApiErrorCode.VALIDATION_ERROR, ApiErrorCode.VALIDATION_ERROR.getDefaultMessage(),
            Map.of("errors", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<Map<String, Object>> errors = exception.getConstraintViolations()
            .stream()
            .map(this::toConstraintViolationDetail)
            .toList();

        return buildResponse(ApiErrorCode.VALIDATION_ERROR, ApiErrorCode.VALIDATION_ERROR.getDefaultMessage(),
            Map.of("errors", errors));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception) {
        return buildResponse(ApiErrorCode.AUTH_INVALID_TOKEN, ApiErrorCode.AUTH_INVALID_TOKEN.getDefaultMessage(),
            Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return buildResponse(ApiErrorCode.ACCESS_DENIED, ApiErrorCode.ACCESS_DENIED.getDefaultMessage(), Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        return buildResponse(ApiErrorCode.SYS_INTERNAL_ERROR, ApiErrorCode.SYS_INTERNAL_ERROR.getDefaultMessage(),
            Map.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
        ApiErrorCode errorCode,
        String message,
        Map<String, Object> details
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
            errorCode.name(),
            message,
            TraceContext.getTraceId(),
            Instant.now(),
            details == null ? Map.of() : details
        );

        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }

    private String resolveMessage(DomainException exception, ApiErrorCode errorCode) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return errorCode.getDefaultMessage();
        }
        return message;
    }

    private Map<String, Object> toFieldErrorDetail(FieldError error) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("field", error.getField());
        detail.put("message", Objects.toString(error.getDefaultMessage(), ""));
        detail.put("rejectedValue", Objects.toString(error.getRejectedValue(), null));
        return detail;
    }

    private Map<String, Object> toConstraintViolationDetail(ConstraintViolation<?> violation) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("field", violation.getPropertyPath().toString());
        detail.put("message", violation.getMessage());
        detail.put("rejectedValue", Objects.toString(violation.getInvalidValue(), null));
        return detail;
    }
}
