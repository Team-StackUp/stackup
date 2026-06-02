package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;

// generate.tts envelope payload (Core → AI). AI 가 text 를 TTS 합성 → S3 PUT → callback.tts 발행.
public record GenerateTtsPayload(
    Long sessionId,
    Long messageId,
    String text,
    SessionMode mode,
    JobCategory jobCategory
) {
}
