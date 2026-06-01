package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.MessageStatus;
import com.stackup.stackup.session.domain.TtsStatus;
import java.time.Instant;

public record MessageResponse(
    Long id,
    Long sessionId,
    Integer sequenceNumber,
    MessageRole role,
    String content,
    String audioFilePath,
    String ttsAudioPath,
    TtsStatus ttsStatus,
    Double ttsDurationSec,
    Long parentMessageId,
    MessageStatus status,
    Instant createdAt
) {
    public static MessageResponse from(MessageResult r) {
        return new MessageResponse(
            r.id(),
            r.sessionId(),
            r.sequenceNumber(),
            r.role(),
            r.content(),
            r.audioFilePath(),
            r.ttsAudioPath(),
            r.ttsStatus(),
            r.ttsDurationSec(),
            r.parentMessageId(),
            r.status(),
            r.createdAt()
        );
    }
}
