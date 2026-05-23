package com.stackup.stackup.github.presentation.dto;

import com.stackup.stackup.github.application.dto.RegisterRepositoryCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRepositoryRequest(
    @NotNull Long githubRepoId,
    @NotBlank String fullName
) {
    public RegisterRepositoryCommand toCommand() {
        return new RegisterRepositoryCommand(githubRepoId, fullName);
    }
}
