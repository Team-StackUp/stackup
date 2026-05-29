package com.stackup.stackup.log.ai.application.dto;

public record AiRequestLogCommand(
    Long userId,
    Long sessionId,
    String requestType,
    String modelName,
    Integer inputTokens,
    Integer outputTokens,
    Integer latencyMs,
    String status,         // SUCCESS | FAILED | TIMEOUT
    String errorMessage
) {
}
