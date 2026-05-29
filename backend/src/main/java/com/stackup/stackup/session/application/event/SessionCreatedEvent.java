package com.stackup.stackup.session.application.event;

import com.stackup.stackup.session.application.dto.GenerateQuestionsPayload;

public record SessionCreatedEvent(
        Long sessionId,
        Long userId,
        GenerateQuestionsPayload payload
) {}
