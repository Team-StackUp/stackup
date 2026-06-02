package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.MessageStatus;
import com.stackup.stackup.session.domain.TtsStatus;
import java.time.Instant;

public record MessageResult(
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
    Instant createdAt,
    String category,
    String targetEvidence
    // expectedSignal 은 의도적으로 제외 — 정답 유출 방지(라이브 비노출).
) {
    public static MessageResult of(InterviewMessage m) {
        return new MessageResult(
            m.getId(),
            m.getSession().getId(),
            m.getSequenceNumber(),
            m.getRole(),
            m.getContent(),
            m.getAudioFilePath(),
            m.getTtsAudioPath(),
            m.getTtsStatus(),
            m.getTtsDurationSec(),
            m.getParentMessage() == null ? null : m.getParentMessage().getId(),
            m.getStatus(),
            m.getCreatedAt(),
            m.getCategory(),
            m.getTargetEvidence()
        );
    }
}
