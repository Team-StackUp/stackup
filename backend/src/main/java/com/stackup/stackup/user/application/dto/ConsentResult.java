package com.stackup.stackup.user.application.dto;

import com.stackup.stackup.user.domain.consent.ConsentType;
import com.stackup.stackup.user.domain.consent.UserConsent;
import java.time.Instant;

public record ConsentResult(
    Long id,
    ConsentType consentType,
    String consentVersion,
    boolean agreed,
    Instant agreedAt,
    Instant revokedAt,
    Instant createdAt
) {
    public static ConsentResult of(UserConsent consent) {
        return new ConsentResult(
            consent.getId(),
            consent.getConsentType(),
            consent.getConsentVersion(),
            consent.isAgreed(),
            consent.getAgreedAt(),
            consent.getRevokedAt(),
            consent.getCreatedAt()
        );
    }
}
