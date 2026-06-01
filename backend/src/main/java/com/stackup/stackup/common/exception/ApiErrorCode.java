package com.stackup.stackup.common.exception;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    AUTH_REVOKED_TOKEN(HttpStatus.UNAUTHORIZED, "폐기된 토큰입니다."),
    AUTH_GITHUB_OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "GitHub OAuth 인증에 실패했습니다."),
    AUTH_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "필수 동의가 필요합니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_ALREADY_DELETED(HttpStatus.GONE, "이미 탈퇴한 사용자입니다."),

    RESUME_INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "PDF 파일만 업로드할 수 있습니다."),
    RESUME_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "업로드 파일 크기가 너무 큽니다."),
    RESUME_EMPTY_FILE(HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다."),
    RESUME_NOT_FOUND(HttpStatus.NOT_FOUND, "이력서를 찾을 수 없습니다."),
    RESUME_IN_USE(HttpStatus.CONFLICT, "사용 중인 이력서입니다."),

    REPO_NOT_FOUND(HttpStatus.NOT_FOUND, "레포지토리를 찾을 수 없습니다."),
    REPO_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 등록된 레포지토리입니다."),
    REPO_GITHUB_API_FAILED(HttpStatus.BAD_GATEWAY, "GitHub API 요청에 실패했습니다."),
    REPO_PRIVATE_NO_ACCESS(HttpStatus.FORBIDDEN, "비공개 레포지토리에 접근할 수 없습니다."),

    DOC_NOT_ANALYZED(HttpStatus.UNPROCESSABLE_CONTENT, "아직 분석되지 않은 문서입니다."),
    DOC_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "문서 분석에 실패했습니다."),
    DOC_NOT_FOUND(HttpStatus.NOT_FOUND, "분석 문서를 찾을 수 없습니다."),
    INTERNAL_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "내부 API 인증에 실패했습니다."),
    EMBEDDING_BAD_REQUEST(HttpStatus.BAD_REQUEST, "임베딩 요청 페이로드가 올바르지 않습니다."),

    SESSION_INVALID_STATE(HttpStatus.UNPROCESSABLE_CONTENT, "세션 상태가 올바르지 않습니다."),
    SESSION_MAX_REACHED(HttpStatus.UNPROCESSABLE_CONTENT, "세션 제한에 도달했습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    SESSION_FORBIDDEN(HttpStatus.FORBIDDEN, "세션에 접근할 수 없습니다."),
    FEEDBACK_NOT_READY(HttpStatus.NOT_FOUND, "피드백이 아직 생성되지 않았습니다."),
    VOICE_EMPTY_FILE(HttpStatus.BAD_REQUEST, "음성 파일을 업로드할 수 없습니다."),
    VOICE_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "음성 파일 크기가 너무 큽니다."),
    VOICE_INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 음성 형식입니다."),
    VOICE_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "음성 메시지를 찾을 수 없습니다."),

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    SYS_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다."),
    SYS_DEPENDENCY_DOWN(HttpStatus.SERVICE_UNAVAILABLE, "필수 외부 의존성이 응답하지 않습니다."),
    SYS_NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "아직 구현되지 않은 기능입니다."),
    SYS_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ApiErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
