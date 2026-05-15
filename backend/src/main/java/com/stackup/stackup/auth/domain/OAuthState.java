package com.stackup.stackup.auth.domain;

import com.stackup.stackup.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "oauth_states")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthState extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state", nullable = false, unique = true, length = 128)
    private String state;

    @Column(name = "code_verifier", nullable = false, length = 128)
    private String codeVerifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private OAuthState(String state, String codeVerifier, OAuthProvider provider, Instant expiresAt) {
        this.state = state;
        this.codeVerifier = codeVerifier;
        this.provider = provider;
        this.expiresAt = expiresAt;
    }

    public static OAuthState issueGithub(String state, String codeVerifier, Instant expiresAt) {
        return new OAuthState(state, codeVerifier, OAuthProvider.GITHUB, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
