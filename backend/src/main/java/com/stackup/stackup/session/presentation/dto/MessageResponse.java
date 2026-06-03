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
    Instant createdAt,
    String category,
    String targetEvidence,
    // 재생용 presigned URL (질문 TTS / 음성 답변 원본). 조회 응답에서만 채움.
    String ttsAudioUrl,
    String audioFileUrl,
    // 질문이 기대한 핵심(평가 관점). 종료된 세션에서만 채워짐(라이브 중엔 null — 정답 유출 방지).
    String expectedSignal
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
            r.createdAt(),
            r.category(),
            r.targetEvidence(),
            r.ttsAudioUrl(),
            r.audioFileUrl(),
            r.expectedSignal()
        );
    }
}
