package com.stackup.stackup.user.presentation.dto;

import com.stackup.stackup.user.application.dto.ConsentResult;
import com.stackup.stackup.user.domain.consent.ConsentType;
import java.time.Instant;

public record ConsentResponse(
    Long id,
    ConsentType consentType,
    String consentVersion,
    boolean agreed,
    Instant agreedAt,
    Instant revokedAt,
    Instant createdAt
) {
    public static ConsentResponse from(ConsentResult result) {
        return new ConsentResponse(
            result.id(),
            result.consentType(),
            result.consentVersion(),
            result.agreed(),
            result.agreedAt(),
            result.revokedAt(),
            result.createdAt()
        );
    }
}
