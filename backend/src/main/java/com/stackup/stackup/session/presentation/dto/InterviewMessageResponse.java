package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.MessageStatus;

import java.time.Instant;

public record InterviewMessageResponse(
    Long id,
    Long sessionId,
    Integer sequenceNumber,
    MessageRole role,
    String content,
    Long parentMessageId,
    MessageStatus status,
    Instant createdAt
) {
    public static InterviewMessageResponse from(MessageResult r) {
        return new InterviewMessageResponse(
            r.id(), r.sessionId(), r.sequenceNumber(), r.role(), r.content(),
            r.parentMessageId(), r.status(), r.createdAt());
    }
}
