package com.stackup.stackup.auth.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthStateRepository extends JpaRepository<OAuthState, Long> {

    Optional<OAuthState> findByState(String state);

    void deleteByExpiresAtBefore(Instant now);
}
