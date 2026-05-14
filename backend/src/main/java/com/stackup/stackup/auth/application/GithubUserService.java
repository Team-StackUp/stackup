package com.stackup.stackup.auth.application;

import com.stackup.stackup.auth.application.dto.AuthenticatedUserResult;
import com.stackup.stackup.auth.application.dto.GithubUserProfile;
import com.stackup.stackup.auth.application.dto.GithubUserUpsertResult;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GithubUserService {

    private final UserRepository userRepository;

    public GithubUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public GithubUserUpsertResult upsertGithubUser(
        GithubUserProfile profile,
        String encryptedGithubAccessToken
    ) {
        return userRepository.findByGithubIdAndDeletedFalse(profile.githubId())
            .map(user -> updateUser(user, profile, encryptedGithubAccessToken))
            .orElseGet(() -> createUser(profile, encryptedGithubAccessToken));
    }

    private GithubUserUpsertResult updateUser(
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
        return new GithubUserUpsertResult(toResult(user), false);
    }

    private GithubUserUpsertResult createUser(
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
        return new GithubUserUpsertResult(toResult(savedUser), true);
    }

    private AuthenticatedUserResult toResult(User user) {
        return new AuthenticatedUserResult(
            user.getId(),
            user.getGithubId(),
            user.getGithubUsername(),
            user.getEmail(),
            user.getAvatarUrl()
        );
    }
}
