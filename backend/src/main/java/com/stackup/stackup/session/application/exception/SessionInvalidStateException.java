package com.stackup.stackup.session.application.exception;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;

public class SessionInvalidStateException extends DomainException {
    public SessionInvalidStateException(Long id, String reason) {
        super(ApiErrorCode.SESSION_INVALID_STATE, "세션 상태 오류: " + reason + ". id=" + id);
    }
}
