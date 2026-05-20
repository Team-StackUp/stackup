package com.stackup.stackup.github.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.infrastructure.GithubTokenCipher;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalGithubTokenService {

    private final UserRepository userRepository;
    private final GithubTokenCipher tokenCipher;

    public String fetchPlainAccessToken(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));
        return tokenCipher.decrypt(user.getEncryptedGithubAccessToken());
    }
}
