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

    /**
     * 회원 탈퇴. soft delete 와 함께 **GitHub access token 을 버린다.**
     *
     * <p>이 토큰은 `repo` 스코프라 비공개 레포까지 읽을 수 있는 살아있는 자격증명이다.
     * hard delete 는 Phase 2 이므로 여기서 지우지 않으면 "삭제해 달라"고 한 사용자의
     * GitHub 접근 권한이 우리 DB 에 무기한 남는다 — DB 가 유출되면 이미 떠난 사람들의
     * 비공개 레포까지 열린다.
     *
     * <p>GitHub 쪽 grant 자체의 무효화는 사용자가 GitHub Settings 에서 해야 한다.
     * 우리가 할 수 있는 것은 사본을 갖지 않는 것까지다.
     */
    public void withdraw() {
        markDeleted();
        this.encryptedGithubAccessToken = null;
    }
}
