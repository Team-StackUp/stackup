package com.stackup.stackup.user.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.user.application.dto.ConsentResult;
import com.stackup.stackup.user.application.dto.ConsentSubmitCommand;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import com.stackup.stackup.user.domain.consent.ConsentType;
import com.stackup.stackup.user.domain.consent.UserConsent;
import com.stackup.stackup.user.domain.consent.UserConsentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 동의 이력은 append-only. 철회 시 가장 최근 활성 row 의 revoked_at 갱신.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserConsentService {

    private final UserRepository userRepository;
    private final UserConsentRepository consentRepository;

    @Transactional
    public ConsentResult submit(Long userId, ConsentSubmitCommand command) {
        User user = loadUser(userId);
        UserConsent consent = consentRepository.save(UserConsent.agree(
            user,
            command.consentType(),
            command.consentVersion(),
            command.ipAddress()
        ));
        return ConsentResult.of(consent);
    }

    public List<ConsentResult> history(Long userId) {
        loadUser(userId);
        return consentRepository.findByUser_IdOrderByIdDesc(userId).stream()
            .map(ConsentResult::of)
            .toList();
    }

    @Transactional
    public void revoke(Long userId, ConsentType consentType) {
        loadUser(userId);
        UserConsent latest = consentRepository
            .findFirstByUser_IdAndConsentTypeAndAgreedTrueAndRevokedAtIsNullOrderByIdDesc(userId, consentType)
            .orElseThrow(() -> new DomainException(ApiErrorCode.VALIDATION_ERROR));
        latest.revoke();
    }

    private User loadUser(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));
    }
}
