package com.stackup.stackup.github.domain;

import com.stackup.stackup.common.entity.BaseSoftDeleteEntity;
import com.stackup.stackup.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "repositories",
        indexes = {
                @Index(name = "idx_repositories_user_id", columnList = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubRepository extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "repo_full_name", nullable = false, length = 500)
    private String repoFullName;

    @Column(name = "repo_url", nullable = false, length = 500)
    private String repoUrl;

    @Column(name = "default_branch", length = 100)
    private String defaultBranch = "main";

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
