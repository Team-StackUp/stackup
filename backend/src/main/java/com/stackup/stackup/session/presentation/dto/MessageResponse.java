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
    String expectedSignal,
    // 답변별 복기(종료 세션에서만). 답변 평가 점수 + 모범 답안 + 리라이트 + 한 줄 코칭.
    Double answerSpecificity,
    Double answerLogic,
    String answerStructure,
    Double answerCorrectness,
    String modelAnswer,
    String answerRewrite,
    String coachingComment,
    // 답변 전달력 메트릭(음성 답변·종료 세션에서만).
    Double speakingRateWpm,
    Double silenceDurationSec,
    java.util.Map<String, Integer> fillerWordCounts,
    Double pronunciationAccuracy,
    // 전달력 메트릭에서 산정한 배지(GOOD/FAIR/POOR)와 한 줄 코칭.
    String deliveryRating,
    String deliveryComment,
    // 오답노트 표시 여부(질문 메시지에만 의미 있음).
    boolean bookmarked
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
            r.expectedSignal(),
            r.answerSpecificity(),
            r.answerLogic(),
            r.answerStructure(),
            r.answerCorrectness(),
            r.modelAnswer(),
            r.answerRewrite(),
            r.coachingComment(),
            r.speakingRateWpm(),
            r.silenceDurationSec(),
            r.fillerWordCounts(),
            r.pronunciationAccuracy(),
            r.deliveryRating(),
            r.deliveryComment(),
            r.bookmarked()
        );
    }
}
