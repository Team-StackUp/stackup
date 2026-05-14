package com.stackup.stackup.common.security;

import com.stackup.stackup.common.exception.ApiErrorCode;
import org.springframework.security.authentication.BadCredentialsException;

public class JwtAuthenticationException extends BadCredentialsException {

    private final ApiErrorCode errorCode;

    public JwtAuthenticationException(ApiErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }
}
