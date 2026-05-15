package com.stackup.stackup.user.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.user.application.dto.UserProfileResult;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public record UserService(UserRepository userRepository) {

    @Transactional(readOnly = true)
    public UserProfileResult getCurrentUser(Long userId) {
        if (userId == null) {
            throw new DomainException(ApiErrorCode.AUTH_INVALID_TOKEN);
        }

        User user = userRepository.findByIdAndDeletedFalse(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));

        return new UserProfileResult(
            user.getId(),
            user.getGithubId(),
            user.getGithubUsername(),
            user.getEmail(),
            user.getAvatarUrl()
        );
    }
}
