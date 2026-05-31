package com.stackup.stackup.session.application.dto;

import java.io.InputStream;

public record VoiceAnswerUploadCommand(
    InputStream content,
    long size,
    String contentType,
    String originalFilename,
    String idempotencyKey
) {
}
