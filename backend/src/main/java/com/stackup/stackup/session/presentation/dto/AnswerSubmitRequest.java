package com.stackup.stackup.session.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerSubmitRequest(
    @NotBlank @Size(max = 10_000) String content
) {}
