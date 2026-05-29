package com.stackup.stackup.session.presentation.dto;

import jakarta.validation.constraints.Size;

public record SessionUpdateRequest(
    @Size(max = 200) String title,
    @Size(max = 4000) String memo
) {
}
