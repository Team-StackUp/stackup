package com.stackup.stackup.github.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RegisterRepositoryRequest(
    @NotNull @Positive Long githubRepoId
) {
}
