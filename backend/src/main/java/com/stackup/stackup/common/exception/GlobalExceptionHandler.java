package com.stackup.stackup.common.exception;

import com.stackup.stackup.common.response.ApiErrorResponse;
import com.stackup.stackup.common.storage.StorageException;
import com.stackup.stackup.common.trace.TraceContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException exception) {
        ApiErrorCode errorCode = exception.getErrorCode();
        log.warn("Domain exception. code={}, traceId={}, details={}",
            errorCode.name(), TraceContext.getTraceId(), exception.getDetails());
        return buildResponse(errorCode, resolveMessage(exception, errorCode), exception.getDetails());
    }

    // ── 잘못된 요청은 4xx 로 끝낸다 ──────────────────────────────────────────────
    //
    // 아래 네 예외는 전부 미처리라 catch-all 로 떨어져 500 + ERROR 로그 + 스택트레이스를
    // 남겼다. 운영 로그의 SYS_INTERNAL_ERROR 11건이 **전부** 이것들이었고 진짜 서버 버그는
    // 0건이었다 — 즉 실제 장애가 나도 오타 URL·스캐너 트래픽에 묻혀 구분이 안 된다.
    // 클라이언트 잘못은 클라이언트에게 알리고(4xx), 로그는 WARN 으로 낮춘다.

    // 본문 누락·깨진 JSON·필드 타입 불일치. 파싱 단계라 @Valid 가 돌기 전에 터진다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.warn("Malformed request body. traceId={}, cause={}",
            TraceContext.getTraceId(), rootCauseName(exception));
        return buildResponse(ApiErrorCode.MALFORMED_REQUEST,
            ApiErrorCode.MALFORMED_REQUEST.getDefaultMessage(), null);
    }

    // /api/sessions/abc 처럼 경로변수를 변환하지 못한 경우.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Path/param type mismatch. traceId={}, name={}",
            TraceContext.getTraceId(), exception.getName());
        return buildResponse(ApiErrorCode.MALFORMED_REQUEST,
            ApiErrorCode.MALFORMED_REQUEST.getDefaultMessage(),
            Map.of("parameter", Objects.toString(exception.getName(), "")));
    }

    // 존재하지 않는 경로. 봇 스캔이 상시 들어오므로 이걸 500 으로 두면 로그가 오염된다.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException exception) {
        log.debug("No handler for path. traceId={}, path={}",
            TraceContext.getTraceId(), exception.getResourcePath());
        return buildResponse(ApiErrorCode.ENDPOINT_NOT_FOUND,
            ApiErrorCode.ENDPOINT_NOT_FOUND.getDefaultMessage(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
        HttpRequestMethodNotSupportedException exception) {
        log.warn("Method not supported. traceId={}, method={}",
            TraceContext.getTraceId(), exception.getMethod());
        return buildResponse(ApiErrorCode.METHOD_NOT_ALLOWED,
            ApiErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage(), null);
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
        ApiErrorCode errorCode = ApiErrorCode.AUTH_INVALID_TOKEN;
        log.warn("Authentication exception. code={}, traceId={}", errorCode.name(), TraceContext.getTraceId());
        return buildResponse(ApiErrorCode.AUTH_INVALID_TOKEN, ApiErrorCode.AUTH_INVALID_TOKEN.getDefaultMessage(),
            Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        ApiErrorCode errorCode = ApiErrorCode.ACCESS_DENIED;
        log.warn("Access denied exception. code={}, traceId={}", errorCode.name(), TraceContext.getTraceId());
        return buildResponse(errorCode, errorCode.getDefaultMessage(), Map.of());
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiErrorResponse> handleStorageException(StorageException exception) {
        return buildResponse(
            ApiErrorCode.SYS_DEPENDENCY_DOWN,
            ApiErrorCode.SYS_DEPENDENCY_DOWN.getDefaultMessage(),
            Map.of("storageErrorType", exception.getType().name())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        ApiErrorCode errorCode = ApiErrorCode.SYS_INTERNAL_ERROR;
        log.error("Unhandled exception. code={}, traceId={}", errorCode.name(), TraceContext.getTraceId(), exception);
        return buildResponse(errorCode, errorCode.getDefaultMessage(), Map.of());
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

    // 예외 타입만 남긴다 — 원문 메시지에는 파싱 실패한 사용자 입력 조각이 섞일 수 있다.
    private static String rootCauseName(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
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
