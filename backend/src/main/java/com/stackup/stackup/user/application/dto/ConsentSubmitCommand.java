package com.stackup.stackup.user.application.dto;

import com.stackup.stackup.user.domain.consent.ConsentType;

public record ConsentSubmitCommand(
    ConsentType consentType,
    String consentVersion,
    String ipAddress
) {
}
