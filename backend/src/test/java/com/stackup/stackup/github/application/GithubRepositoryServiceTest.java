package com.stackup.stackup.github.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.domain.GithubRepository;
import com.stackup.stackup.github.domain.GithubRepositoryRepository;
import com.stackup.stackup.github.infrastructure.GithubApiClient;
import com.stackup.stackup.github.infrastructure.GithubTokenCipher;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class GithubRepositoryServiceTest {

    private GithubRepositoryRepository repoRepository;
    private UserRepository userRepository;
    private GithubApiClient githubApiClient;
    private GithubTokenCipher tokenCipher;
    private ApplicationEventPublisher events;
    private GithubRepositoryService service;

    @BeforeEach
    void setUp() {
        repoRepository = Mockito.mock(GithubRepositoryRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        githubApiClient = Mockito.mock(GithubApiClient.class);
        tokenCipher = Mockito.mock(GithubTokenCipher.class);
        events = Mockito.mock(ApplicationEventPublisher.class);
        service = new GithubRepositoryService(
            repoRepository, userRepository, githubApiClient, tokenCipher, events
        );
    }

    @Test
    void list_returns_page_of_user_repositories() {
        var pageable = PageRequest.of(0, 20);
        GithubRepository repo = GithubRepository.create(
            Mockito.mock(User.class), 1L, "n", "u/n", "https://x", "main"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(repo, "id", 7L);
        var page = new PageImpl<>(java.util.List.of(repo), pageable, 1);
        when(repoRepository.findByUser_IdAndDeletedFalse(eq(42L), eq(pageable))).thenReturn(page);

        var result = service.list(42L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(7L);
    }

    @Test
    void get_throws_when_not_found_or_other_user() {
        when(repoRepository.findByIdAndUser_IdAndDeletedFalse(1L, 42L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.get(42L, 1L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.REPO_NOT_FOUND);
    }

    @Test
    void get_returns_repository_when_owner() {
        GithubRepository repo = GithubRepository.create(
            Mockito.mock(User.class), 1L, "n", "u/n", "https://x", "main"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(repo, "id", 1L);
        when(repoRepository.findByIdAndUser_IdAndDeletedFalse(1L, 42L)).thenReturn(java.util.Optional.of(repo));

        var result = service.get(42L, 1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void delete_throws_repo_not_found_for_unknown() {
        when(repoRepository.findByIdAndUser_IdAndDeletedFalse(1L, 42L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.delete(42L, 1L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.REPO_NOT_FOUND);
    }

    @Test
    void delete_marks_repository_deleted_when_present() {
        GithubRepository repo = GithubRepository.create(
            Mockito.mock(User.class), 1L, "n", "u/n", "https://x", "main"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(repo, "id", 1L);
        when(repoRepository.findByIdAndUser_IdAndDeletedFalse(1L, 42L)).thenReturn(java.util.Optional.of(repo));

        service.delete(42L, 1L);

        assertThat(repo.isDeleted()).isTrue();
    }
}
