package com.stackup.stackup.github.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.user.domain.User;
import org.junit.jupiter.api.Test;

class GithubRepositoryTest {

    @Test
    void create_initializes_pending_repository() {
        User user = org.mockito.Mockito.mock(User.class);
        GithubRepository repo = GithubRepository.create(
            user, 1296269L, "Hello-World", "octocat/Hello-World",
            "https://github.com/octocat/Hello-World", "main"
        );

        assertThat(repo.getGithubRepoId()).isEqualTo(1296269L);
        assertThat(repo.getRepoName()).isEqualTo("Hello-World");
        assertThat(repo.getRepoFullName()).isEqualTo("octocat/Hello-World");
        assertThat(repo.getRepoUrl()).isEqualTo("https://github.com/octocat/Hello-World");
        assertThat(repo.getDefaultBranch()).isEqualTo("main");
        assertThat(repo.getStatus()).isEqualTo(RepositoryStatus.PENDING);
        assertThat(repo.isDeleted()).isFalse();
    }

    @Test
    void resurrect_revives_deleted_repository_and_refreshes_metadata() {
        User user = org.mockito.Mockito.mock(User.class);
        GithubRepository repo = GithubRepository.create(
            user, 1L, "old", "u/old", "https://x", "master"
        );
        repo.softDelete();
        assertThat(repo.isDeleted()).isTrue();

        repo.resurrect("new", "u/new", "https://y", "main");

        assertThat(repo.isDeleted()).isFalse();
        assertThat(repo.getRepoName()).isEqualTo("new");
        assertThat(repo.getRepoFullName()).isEqualTo("u/new");
        assertThat(repo.getRepoUrl()).isEqualTo("https://y");
        assertThat(repo.getDefaultBranch()).isEqualTo("main");
        assertThat(repo.getStatus()).isEqualTo(RepositoryStatus.PENDING);
    }
}
