package com.stackup.stackup.github.domain;

import com.stackup.stackup.common.entity.BaseSoftDeleteEntity;
import com.stackup.stackup.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "repositories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_repositories_user_github_repo", columnNames = {"user_id", "github_repo_id"})
        },
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
    @Enumerated(EnumType.STRING)
    private RepositoryStatus status = RepositoryStatus.PENDING;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    public void markAnalyzing() {
        this.status = RepositoryStatus.ANALYZING;
    }

    public void markAnalyzed() {
        this.status = RepositoryStatus.ANALYZED;
        this.lastSyncedAt = Instant.now();
    }

    public void markFailed() {
        this.status = RepositoryStatus.FAILED;
    }
}
