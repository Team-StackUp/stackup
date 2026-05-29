package com.stackup.stackup.session.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageSubmitRequest(
    @NotBlank @Size(max = 8000) String content
) {
}
