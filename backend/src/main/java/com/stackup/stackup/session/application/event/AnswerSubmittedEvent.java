package com.stackup.stackup.session.application.event;

// 답변 INSERT commit 후 발행. 인프라스트럭처가 받아 generate.followup 발행.
public record AnswerSubmittedEvent(
    Long userId,
    Long sessionId,
    Long parentQuestionMessageId,
    Long answerMessageId
) {
}
