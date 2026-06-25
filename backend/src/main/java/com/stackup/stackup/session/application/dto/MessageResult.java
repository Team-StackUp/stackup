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
    String expectedSignal,
    // 답변별 복기 — 종료 세션 조회에서만 노출(expectedSignal 과 동일 게이팅). 라이브 중엔 모두 null.
    Double answerSpecificity,
    Double answerLogic,
    String answerStructure,
    Double answerCorrectness,
    String modelAnswer,
    String answerRewrite,
    String coachingComment
) {
    public static MessageResult of(InterviewMessage m) {
        return of(m, null, null, false);
    }

    public static MessageResult of(InterviewMessage m, String ttsAudioUrl, String audioFileUrl) {
        return of(m, ttsAudioUrl, audioFileUrl, false);
    }

    // revealInsights=true 면 답변 평가·복기·expectedSignal 노출(종료 세션). 라이브/대기 중엔 false.
    public static MessageResult of(
        InterviewMessage m, String ttsAudioUrl, String audioFileUrl, boolean revealInsights) {
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
            revealInsights ? m.getExpectedSignal() : null,
            revealInsights ? m.getAnswerSpecificity() : null,
            revealInsights ? m.getAnswerLogic() : null,
            revealInsights ? m.getAnswerStructure() : null,
            revealInsights ? m.getAnswerCorrectness() : null,
            revealInsights ? m.getModelAnswer() : null,
            revealInsights ? m.getAnswerRewrite() : null,
            revealInsights ? m.getCoachingComment() : null
        );
    }
}
