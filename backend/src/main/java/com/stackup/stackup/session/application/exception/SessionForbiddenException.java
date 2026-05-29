package com.stackup.stackup.session.application.exception;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;

public class SessionForbiddenException extends DomainException {
    public SessionForbiddenException(Long id) {
        super(ApiErrorCode.SESSION_FORBIDDEN, "세션 접근 권한이 없습니다. id=" + id);
    }
}
