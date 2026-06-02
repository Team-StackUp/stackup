package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.VoiceStreamBeginResult;

public record VoiceStreamBeginResponse(Long messageId, Long parentMessageId, Integer sequenceNumber) {
    public static VoiceStreamBeginResponse from(VoiceStreamBeginResult r) {
        return new VoiceStreamBeginResponse(r.messageId(), r.parentMessageId(), r.sequenceNumber());
    }
}
