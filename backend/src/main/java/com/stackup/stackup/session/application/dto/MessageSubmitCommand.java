package com.stackup.stackup.session.application.dto;

public record MessageSubmitCommand(
        Long sessionId,
        Long userId,
        String content,
        String idempotencyKey
) {}
