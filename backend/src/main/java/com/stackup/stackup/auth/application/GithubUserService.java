package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.application.dto.AuthenticatedUserResult;
import com.stackup.stackup.auth.application.dto.GithubUserProfile;
import com.stackup.stackup.auth.application.dto.OAuthUserUpsertResult;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// record 는 implicit final 이라 Spring AOP(@Transactional) CGLIB 프록시 생성 실패.
// 일반 class + 생성자 주입으로 전환.
@Service
public class GithubUserService {

    private final UserRepository userRepository;

    public GithubUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public OAuthUserUpsertResult upsertGithubUser(
        GithubUserProfile profile,
        String encryptedGithubAccessToken
    ) {
        return userRepository.findByGithubIdAndDeletedFalse(profile.githubId())
            .map(user -> updateUser(user, profile, encryptedGithubAccessToken))
            .orElseGet(() -> createUser(profile, encryptedGithubAccessToken));
    }

    private OAuthUserUpsertResult updateUser(
        User user,
        GithubUserProfile profile,
        String encryptedGithubAccessToken
    ) {
        user.updateGithubProfile(
            profile.githubUsername(),
            profile.email(),
            profile.avatarUrl(),
            encryptedGithubAccessToken
        );
        return new OAuthUserUpsertResult(toResult(user), false);
    }

    private OAuthUserUpsertResult createUser(
        GithubUserProfile profile,
        String encryptedGithubAccessToken
    ) {
        User user = User.createGithubUser(
            profile.githubId(),
            profile.githubUsername(),
            profile.email(),
            profile.avatarUrl(),
            encryptedGithubAccessToken
        );
        User savedUser = userRepository.save(user);
        return new OAuthUserUpsertResult(toResult(savedUser), true);
    }

    private AuthenticatedUserResult toResult(User user) {
        return AuthUserResults.from(user);
    }
}
