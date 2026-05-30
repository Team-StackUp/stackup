package com.stackup.stackup.session.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

// callback.voice (AI → Core). STT 결과 transcript + 음성 분석 지표.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoiceCallbackPayload(
    Long sessionId,
    Long interviewMessageId,
    String transcript,
    Double speakingRateWpm,
    Double silenceDurationSec,
    Map<String, Integer> fillerWordCounts,   // {"음":3, "어":5, "그":2}
    Double pronunciationAccuracy,            // 0~1 (Whisper logprob 기반, null 허용)
    String errorCode                         // STT 실패 시 코드 (TRANSCRIPTION_FAILED 등)
) {
}
