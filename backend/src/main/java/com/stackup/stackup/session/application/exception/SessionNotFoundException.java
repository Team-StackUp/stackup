package com.stackup.stackup.session.application.exception;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;

public class SessionNotFoundException extends DomainException {
    public SessionNotFoundException(Long id) {
        super(ApiErrorCode.SESSION_NOT_FOUND, "세션을 찾을 수 없습니다. id=" + id);
    }
}
