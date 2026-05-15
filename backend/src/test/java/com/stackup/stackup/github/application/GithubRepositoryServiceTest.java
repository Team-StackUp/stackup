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

    @Test
    void list_candidates_proxies_github_and_marks_already_registered() {
        com.stackup.stackup.user.domain.User user = Mockito.mock(com.stackup.stackup.user.domain.User.class);
        when(user.getEncryptedGithubAccessToken()).thenReturn("encrypted");
        when(userRepository.findById(42L)).thenReturn(java.util.Optional.of(user));
        when(tokenCipher.decrypt("encrypted")).thenReturn("plain-token");

        var repoA = new com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse(
            1L, "a", "u/a", "https://x/a", "main", false, "d");
        var repoB = new com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse(
            2L, "b", "u/b", "https://x/b", "main", true, "d");
        var listPage = new com.stackup.stackup.github.infrastructure.dto.GithubRepoListPage(
            java.util.List.of(repoA, repoB), true);
        when(githubApiClient.listUserRepositories("plain-token", 1, 30)).thenReturn(listPage);

        when(repoRepository.findGithubRepoIdsByUser_IdAndDeletedFalseAndGithubRepoIdIn(
            eq(42L), eq(java.util.Set.of(1L, 2L))
        )).thenReturn(java.util.List.of(2L));

        var result = service.listCandidates(42L, 1, 30);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).githubRepoId()).isEqualTo(1L);
        assertThat(result.content().get(0).alreadyRegistered()).isFalse();
        assertThat(result.content().get(1).githubRepoId()).isEqualTo(2L);
        assertThat(result.content().get(1).alreadyRegistered()).isTrue();
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.perPage()).isEqualTo(30);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void list_candidates_throws_when_user_not_found() {
        when(userRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.listCandidates(99L, 1, 30))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.USER_NOT_FOUND);
    }

    @Test
    void register_happy_path_persists_repo_and_publishes_event() {
        com.stackup.stackup.user.domain.User user = Mockito.mock(com.stackup.stackup.user.domain.User.class);
        when(user.getEncryptedGithubAccessToken()).thenReturn("enc");
        when(userRepository.findById(42L)).thenReturn(java.util.Optional.of(user));
        when(userRepository.getReferenceById(42L)).thenReturn(user);
        when(tokenCipher.decrypt("enc")).thenReturn("plain");
        when(tokenCipher.encrypt("plain")).thenReturn("sealed");
        when(githubApiClient.getRepository("plain", 1296269L)).thenReturn(
            new com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse(
                1296269L, "Hello-World", "octocat/Hello-World",
                "https://github.com/octocat/Hello-World", "main", false, "d")
        );
        when(repoRepository.findByUser_IdAndGithubRepoId(42L, 1296269L))
            .thenReturn(java.util.Optional.empty());
        when(repoRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            GithubRepository r = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 7L);
            org.springframework.test.util.ReflectionTestUtils.setField(r, "createdAt", java.time.Instant.now());
            org.springframework.test.util.ReflectionTestUtils.setField(r, "updatedAt", java.time.Instant.now());
            return r;
        });

        var result = service.register(42L,
            new com.stackup.stackup.github.application.dto.RegisterRepositoryCommand(1296269L));

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.repoFullName()).isEqualTo("octocat/Hello-World");

        var captor = org.mockito.ArgumentCaptor.forClass(
            com.stackup.stackup.github.application.event.RepositoryRegisteredEvent.class);
        Mockito.verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().repositoryId()).isEqualTo(7L);
        assertThat(captor.getValue().userId()).isEqualTo(42L);
        assertThat(captor.getValue().githubFullName()).isEqualTo("octocat/Hello-World");
        assertThat(captor.getValue().defaultBranch()).isEqualTo("main");
        assertThat(captor.getValue().encryptedGithubAccessToken()).isEqualTo("sealed");
    }

    @Test
    void register_resurrects_deleted_row_instead_of_409() {
        com.stackup.stackup.user.domain.User user = Mockito.mock(com.stackup.stackup.user.domain.User.class);
        when(user.getEncryptedGithubAccessToken()).thenReturn("enc");
        when(userRepository.findById(42L)).thenReturn(java.util.Optional.of(user));
        when(tokenCipher.decrypt("enc")).thenReturn("plain");
        when(tokenCipher.encrypt("plain")).thenReturn("sealed");
        when(githubApiClient.getRepository("plain", 1L)).thenReturn(
            new com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse(
                1L, "new", "u/new", "https://x", "main", false, "d")
        );

        GithubRepository deletedRow = GithubRepository.create(user, 1L, "old", "u/old", "https://x", "master");
        org.springframework.test.util.ReflectionTestUtils.setField(deletedRow, "id", 5L);
        deletedRow.softDelete();
        when(repoRepository.findByUser_IdAndGithubRepoId(42L, 1L))
            .thenReturn(java.util.Optional.of(deletedRow));

        var result = service.register(42L,
            new com.stackup.stackup.github.application.dto.RegisterRepositoryCommand(1L));

        assertThat(result.id()).isEqualTo(5L);
        assertThat(deletedRow.isDeleted()).isFalse();
        assertThat(deletedRow.getRepoFullName()).isEqualTo("u/new");
        Mockito.verify(events).publishEvent(
            org.mockito.ArgumentMatchers.any(
                com.stackup.stackup.github.application.event.RepositoryRegisteredEvent.class));
    }

    @Test
    void register_throws_409_when_active_row_already_exists() {
        com.stackup.stackup.user.domain.User user = Mockito.mock(com.stackup.stackup.user.domain.User.class);
        when(user.getEncryptedGithubAccessToken()).thenReturn("enc");
        when(userRepository.findById(42L)).thenReturn(java.util.Optional.of(user));
        when(tokenCipher.decrypt("enc")).thenReturn("plain");
        when(githubApiClient.getRepository("plain", 1L)).thenReturn(
            new com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse(
                1L, "n", "u/n", "https://x", "main", false, "d")
        );

        GithubRepository activeRow = GithubRepository.create(user, 1L, "n", "u/n", "https://x", "main");
        org.springframework.test.util.ReflectionTestUtils.setField(activeRow, "id", 9L);
        when(repoRepository.findByUser_IdAndGithubRepoId(42L, 1L))
            .thenReturn(java.util.Optional.of(activeRow));

        assertThatThrownBy(() -> service.register(42L,
            new com.stackup.stackup.github.application.dto.RegisterRepositoryCommand(1L)))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.REPO_ALREADY_REGISTERED);

        Mockito.verify(events, Mockito.never()).publishEvent(
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void register_propagates_private_no_access_from_api_client() {
        com.stackup.stackup.user.domain.User user = Mockito.mock(com.stackup.stackup.user.domain.User.class);
        when(user.getEncryptedGithubAccessToken()).thenReturn("enc");
        when(userRepository.findById(42L)).thenReturn(java.util.Optional.of(user));
        when(tokenCipher.decrypt("enc")).thenReturn("plain");
        when(githubApiClient.getRepository("plain", 99L)).thenThrow(
            new DomainException(ApiErrorCode.REPO_PRIVATE_NO_ACCESS));

        assertThatThrownBy(() -> service.register(42L,
            new com.stackup.stackup.github.application.dto.RegisterRepositoryCommand(99L)))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.REPO_PRIVATE_NO_ACCESS);
    }
}
