package com.stackup.stackup.user.domain;

import com.stackup.stackup.user.domain.OAuthProvider;
import com.stackup.stackup.common.entity.BaseSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private OAuthProvider provider;

    /** 화면에 노출되는 이름. GitHub 은 login, Google 은 name(없으면 이메일 로컬파트). */
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    // GitHub 계정에만 존재. Google 계정은 셋 다 null 이다.
    @Column(name = "github_id")
    private Long githubId;

    @Column(name = "github_username", length = 100)
    private String githubUsername;

    @Column(name = "encrypted_github_access_token", length = 1000)
    private String encryptedGithubAccessToken;

    /** Google 계정에만 존재. Google 의 sub 은 숫자열이지만 문자열로 다루도록 규정돼 있다. */
    @Column(name = "google_id", length = 255)
    private String googleId;

    @Column(length = 255)
    private String email;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    private User(
        OAuthProvider provider,
        String displayName,
        Long githubId,
        String githubUsername,
        String encryptedGithubAccessToken,
        String googleId,
        String email,
        String avatarUrl
    ) {
        this.provider = provider;
        this.displayName = displayName;
        this.githubId = githubId;
        this.githubUsername = githubUsername;
        this.encryptedGithubAccessToken = encryptedGithubAccessToken;
        this.googleId = googleId;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    public static User createGithubUser(
        Long githubId,
        String githubUsername,
        String email,
        String avatarUrl,
        String encryptedGithubAccessToken
    ) {
        return new User(
            OAuthProvider.GITHUB,
            githubUsername,
            githubId,
            githubUsername,
            encryptedGithubAccessToken,
            null,
            email,
            avatarUrl
        );
    }

    public static User createGoogleUser(
        String googleId,
        String displayName,
        String email,
        String avatarUrl
    ) {
        return new User(
            OAuthProvider.GOOGLE,
            displayName,
            null,
            null,
            null,
            googleId,
            email,
            avatarUrl
        );
    }

    public void updateGithubProfile(
        String githubUsername,
        String email,
        String avatarUrl,
        String encryptedGithubAccessToken
    ) {
        this.githubUsername = githubUsername;
        this.displayName = githubUsername;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.encryptedGithubAccessToken = encryptedGithubAccessToken;
    }

    public void updateGoogleProfile(String displayName, String email, String avatarUrl) {
        this.displayName = displayName;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    /** GitHub 연동이 필요한 기능(레포 조회·분석)을 쓸 수 있는 계정인지. */
    public boolean hasGithubLink() {
        return encryptedGithubAccessToken != null && !encryptedGithubAccessToken.isBlank();
    }

    public void markDeleted() {
        this.deleted = true;
    }
}
