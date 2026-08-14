package com.stackup.stackup.user.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGithubId(Long githubId);

    Optional<User> findByGithubIdAndDeletedFalse(Long githubId);

    Optional<User> findByIdAndDeletedFalse(Long id);

    Optional<User> findByGithubUsername(String githubUsername);

    Optional<User> findByGoogleIdAndDeletedFalse(String googleId);
}
