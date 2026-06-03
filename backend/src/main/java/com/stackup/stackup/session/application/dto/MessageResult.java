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
    String audioFileUrl,
    // 질문이 기대한 핵심(평가 관점). 라이브 중엔 null(정답 유출 방지),
    // 종료된 세션 조회에서만 채워 피드백 학습용으로 노출.
    String expectedSignal
) {
    public static MessageResult of(InterviewMessage m) {
        return of(m, null, null, null);
    }

    public static MessageResult of(InterviewMessage m, String ttsAudioUrl, String audioFileUrl) {
        return of(m, ttsAudioUrl, audioFileUrl, null);
    }

    public static MessageResult of(
        InterviewMessage m, String ttsAudioUrl, String audioFileUrl, String expectedSignal) {
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
            audioFileUrl,
            expectedSignal
        );
    }
}
