package com.stackup.stackup.profile.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJobProfileRepository extends JpaRepository<UserJobProfile, Long> {
    Optional<UserJobProfile> findByUserId(Long userId);
}
