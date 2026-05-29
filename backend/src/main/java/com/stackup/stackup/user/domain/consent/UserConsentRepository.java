package com.stackup.stackup.user.domain.consent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findByUser_IdOrderByIdDesc(Long userId);

    Optional<UserConsent> findFirstByUser_IdAndConsentTypeAndAgreedTrueAndRevokedAtIsNullOrderByIdDesc(
        Long userId,
        ConsentType consentType
    );
}
