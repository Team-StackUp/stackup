package com.stackup.stackup.user.presentation.dto;

import com.stackup.stackup.user.application.dto.ConsentSubmitCommand;
import com.stackup.stackup.user.domain.consent.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsentSubmitRequest(
    @NotNull ConsentType consentType,
    @NotBlank String consentVersion
) {
    public ConsentSubmitCommand toCommand(String ipAddress) {
        return new ConsentSubmitCommand(consentType, consentVersion, ipAddress);
    }
}
