package com.stackup.stackup.session.application.event;

// 음성 답변 오디오 키가 메시지에 붙은 뒤 발행. VoiceAnalysisRequester 가 AFTER_COMMIT 에 받아
// analyze.voice 를 발행한다. commit 전에 발행하면 롤백 시 AI 가 존재하지 않는 메시지로 STT 를
// 돌리고, 콜백은 "message not found" 로 드롭되어 사용자 답변이 조용히 증발한다.
public record VoiceAnswerUploadedEvent(
    Long userId,
    Long sessionId,
    Long messageId,
    String audioS3Key,
    String contentType
) {
}
