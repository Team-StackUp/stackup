package com.stackup.stackup.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DomainException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final Map<String, Object> details;

    public DomainException(ApiErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), Map.of());
    }

    public DomainException(ApiErrorCode errorCode, Map<String, Object> details) {
        this(errorCode, errorCode.getDefaultMessage(), details);
    }

    public DomainException(ApiErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public DomainException(ApiErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = copyDetails(details);
    }

    public DomainException(ApiErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, Map.of());
    }

    public DomainException(ApiErrorCode errorCode, String message, Throwable cause, Map<String, Object> details) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = copyDetails(details);
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    private Map<String, Object> copyDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}
