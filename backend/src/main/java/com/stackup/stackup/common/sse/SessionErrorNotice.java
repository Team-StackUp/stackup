package com.stackup.stackup.common.sse;

// SseEventType.ERROR 의 세션 도메인 payload (docs/event-stream.md §3.6).
// scope 로 실패 지점을 구분한다 (FOLLOWUP | FEEDBACK).
// errorMessage 가 아니라 message — AI 내부 원문이 아니라 사용자에게 보여줄 문구다
// (원문 화이트리스트 책임은 각 콜백 서비스에 있다).
public record SessionErrorNotice(
    Long sessionId, String scope, String errorCode, String message, Boolean retriable
) {
}
