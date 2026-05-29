package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.MessageStatus;
import java.time.Instant;

public record MessageResult(
    Long id,
    Long sessionId,
    Integer sequenceNumber,
    MessageRole role,
    String content,
    Long parentMessageId,
    MessageStatus status,
    Instant createdAt
) {
    public static MessageResult of(InterviewMessage m) {
        return new MessageResult(
            m.getId(),
            m.getSession().getId(),
            m.getSequenceNumber(),
            m.getRole(),
            m.getContent(),
            m.getParentMessage() == null ? null : m.getParentMessage().getId(),
            m.getStatus(),
            m.getCreatedAt()
        );
    }
}
