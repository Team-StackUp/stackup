package com.stackup.stackup.session.application.event;

// 세션 종료(COMPLETED 전이) commit 후 발화. FeedbackRequester 가 수신.
// 사유: USER_REQUEST | MAX_QUESTIONS_REACHED | POOL_EXHAUSTED
public record SessionEndedEvent(
    Long userId,
    Long sessionId,
    String reason
) {
}
