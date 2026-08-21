package com.stackup.stackup.user.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.user.application.dto.UserProfileResult;
import com.stackup.stackup.user.application.event.UserDeletedEvent;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// record 는 final → Spring AOP CGLIB 프록시 실패. class 로 전환.
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public UserService(UserRepository userRepository, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public UserProfileResult getCurrentUser(Long userId) {
        if (userId == null) {
            throw new DomainException(ApiErrorCode.AUTH_INVALID_TOKEN);
        }

        User user = userRepository.findByIdAndDeletedFalse(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));

        return new UserProfileResult(
            user.getId(),
            user.getProvider(),
            user.getDisplayName(),
            user.getGithubId(),
            user.getGithubUsername(),
            user.getEmail(),
            user.getAvatarUrl()
        );
    }

    // 회원 탈퇴: User soft delete + 보관 중이던 GitHub access token 폐기 + UserDeletedEvent 발행.
    // auth 슬라이스 listener 가 모든 refresh token 을, session 슬라이스 listener 가 공유
    // 토큰을 revoke 한다 (도메인 분리).
    // GitHub 쪽 grant 자체의 무효화는 사용자가 GitHub Settings 에서 별도 수행해야 한다 —
    // 우리가 할 수 있는 건 사본을 갖지 않는 것까지다(User.withdraw).
    @Transactional
    public void deleteAccount(Long userId) {
        if (userId == null) {
            throw new DomainException(ApiErrorCode.AUTH_INVALID_TOKEN);
        }
        User user = userRepository.findByIdAndDeletedFalse(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_ALREADY_DELETED));
        user.withdraw();
        events.publishEvent(new UserDeletedEvent(userId));
    }
}
