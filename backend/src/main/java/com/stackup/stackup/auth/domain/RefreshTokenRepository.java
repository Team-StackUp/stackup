package com.stackup.stackup.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser_Id(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE RefreshToken rt
           SET rt.revoked = true
         WHERE rt.user.id = :userId
           AND rt.revoked = false
        """)
    int revokeAllByUserId(@Param("userId") Long userId);
}
