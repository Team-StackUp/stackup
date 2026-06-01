package com.stackup.stackup.session.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InternalAnswerSubmitRequest(
    @NotNull Long userId,
    @NotBlank String content,
    String idempotencyKey
) {
}
