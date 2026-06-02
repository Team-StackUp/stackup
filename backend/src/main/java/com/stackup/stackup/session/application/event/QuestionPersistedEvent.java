package com.stackup.stackup.session.application.event;

// INTERVIEWER 질문 메시지 영속 후 발행. SessionTtsRequester 가 AFTER_COMMIT 에 받아 generate.tts 발행.
public record QuestionPersistedEvent(
    Long userId,
    Long sessionId,
    Long messageId
) {
}
