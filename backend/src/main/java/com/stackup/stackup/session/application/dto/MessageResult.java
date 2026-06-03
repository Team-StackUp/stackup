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
    String targetEvidence,
    // 재생용 presigned URL. 조회(list) 경로에서만 채우고, 그 외에는 null.
    String ttsAudioUrl,
    String audioFileUrl
    // expectedSignal 은 의도적으로 제외 — 정답 유출 방지(라이브 비노출).
) {
    public static MessageResult of(InterviewMessage m) {
        return of(m, null, null);
    }

    public static MessageResult of(InterviewMessage m, String ttsAudioUrl, String audioFileUrl) {
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
            m.getTargetEvidence(),
            ttsAudioUrl,
            audioFileUrl
        );
    }
}
