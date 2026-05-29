package com.stackup.stackup.session.application.event;

public record AnswerSubmittedEvent(
        Long sessionId,
        Long userId,
        Long parentQuestionMessageId,
        Long answerMessageId,
        String answerText
) {}
