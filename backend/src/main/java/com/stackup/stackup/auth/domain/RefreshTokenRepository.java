package com.stackup.stackup.auth.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 만료된 refresh token 은 더 이상 어떤 판단에도 쓰이지 않는다 — 검증은 항상
    // 만료 시각을 확인하므로 남겨둬도 무효이고, 쌓이기만 한다.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpiredBefore(@Param("now") Instant now);

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
