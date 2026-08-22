package com.stackup.stackup.session.application.event;

// 피드백 재생성 요청 commit 후 발화. FeedbackRequester 가 수신.
// regenerate 트랜잭션(실패 마커 clear 포함)이 커밋된 뒤에만 generate.feedback 이 발행되게 해
// "메시지 발행은 commit 이후" 규칙(backend/CLAUDE.md)을 지킨다 — 발행 후 커밋 실패로
// 마커가 남거나, clear 커밋 전에 새 시도의 콜백이 도착하는 역전을 막는다.
public record FeedbackRegenerateRequestedEvent(
    Long userId,
    Long sessionId
) {
}
