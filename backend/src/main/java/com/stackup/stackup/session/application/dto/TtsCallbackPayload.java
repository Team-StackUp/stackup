package com.stackup.stackup.session.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TtsCallbackPayload(
    Long sessionId,
    Long messageId,
    String status,
    @JsonAlias({"audioPath", "audioUrl"})
    String audioKey,
    Double durationSec,
    String errorCode
) {
    public boolean isSucceeded() {
        return "SUCCEEDED".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status) || (errorCode != null && !errorCode.isBlank());
    }
}
