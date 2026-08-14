package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.application.dto.GoogleUserProfile;
import com.stackup.stackup.auth.application.dto.OAuthUserUpsertResult;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// record 는 implicit final 이라 Spring AOP(@Transactional) CGLIB 프록시 생성 실패.
// 일반 class + 생성자 주입으로 전환. (GithubUserService 와 동일)
@Service
public class GoogleUserService {

    private final UserRepository userRepository;

    public GoogleUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Google 계정을 upsert 한다.
     *
     * 같은 이메일의 GitHub 계정이 있어도 합치지 않는다 — 이메일은 소유가 바뀔 수 있어
     * 신원의 근거로 삼으면 계정 탈취 경로가 된다. 두 provider 를 한 계정으로 묶는 건
     * 로그인한 상태에서 명시적으로 연결하는 별도 기능이어야 한다.
     */
    @Transactional
    public OAuthUserUpsertResult upsertGoogleUser(GoogleUserProfile profile) {
        return userRepository.findByGoogleIdAndDeletedFalse(profile.googleId())
            .map(user -> updateUser(user, profile))
            .orElseGet(() -> createUser(profile));
    }

    private OAuthUserUpsertResult updateUser(User user, GoogleUserProfile profile) {
        user.updateGoogleProfile(profile.displayName(), profile.email(), profile.avatarUrl());
        return new OAuthUserUpsertResult(AuthUserResults.from(user), false);
    }

    private OAuthUserUpsertResult createUser(GoogleUserProfile profile) {
        User savedUser = userRepository.save(User.createGoogleUser(
            profile.googleId(),
            profile.displayName(),
            profile.email(),
            profile.avatarUrl()
        ));
        return new OAuthUserUpsertResult(AuthUserResults.from(savedUser), true);
    }
}
