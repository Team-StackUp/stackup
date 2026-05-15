package com.stackup.stackup.github.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.application.dto.GithubRepositoryResult;
import com.stackup.stackup.github.domain.GithubRepository;
import com.stackup.stackup.github.domain.GithubRepositoryRepository;
import com.stackup.stackup.github.infrastructure.GithubApiClient;
import com.stackup.stackup.github.infrastructure.GithubTokenCipher;
import com.stackup.stackup.user.domain.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GithubRepositoryService {

    private final GithubRepositoryRepository repoRepository;
    private final UserRepository userRepository;
    private final GithubApiClient githubApiClient;
    private final GithubTokenCipher tokenCipher;
    private final ApplicationEventPublisher events;

    public GithubRepositoryService(
        GithubRepositoryRepository repoRepository,
        UserRepository userRepository,
        GithubApiClient githubApiClient,
        GithubTokenCipher tokenCipher,
        ApplicationEventPublisher events
    ) {
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
        this.githubApiClient = githubApiClient;
        this.tokenCipher = tokenCipher;
        this.events = events;
    }

    public Page<GithubRepositoryResult> list(Long userId, Pageable pageable) {
        return repoRepository.findByUser_IdAndDeletedFalse(userId, pageable)
            .map(GithubRepositoryResult::from);
    }

    public GithubRepositoryResult get(Long userId, Long repositoryId) {
        return repoRepository.findByIdAndUser_IdAndDeletedFalse(repositoryId, userId)
            .map(GithubRepositoryResult::from)
            .orElseThrow(() -> new DomainException(ApiErrorCode.REPO_NOT_FOUND));
    }

    @Transactional
    public void delete(Long userId, Long repositoryId) {
        GithubRepository repo = repoRepository.findByIdAndUser_IdAndDeletedFalse(repositoryId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.REPO_NOT_FOUND));
        repo.softDelete();
    }

    public com.stackup.stackup.github.application.dto.CandidateRepositoryListResult listCandidates(
        Long userId, int page, int perPage
    ) {
        com.stackup.stackup.user.domain.User user = userRepository.findById(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));

        String token = tokenCipher.decrypt(user.getEncryptedGithubAccessToken());
        com.stackup.stackup.github.infrastructure.dto.GithubRepoListPage listPage =
            githubApiClient.listUserRepositories(token, page, perPage);

        java.util.Set<Long> githubIds = listPage.repos().stream()
            .map(com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse::id)
            .collect(java.util.stream.Collectors.toSet());

        java.util.Set<Long> registered = githubIds.isEmpty()
            ? java.util.Set.of()
            : new java.util.HashSet<>(repoRepository
                .findGithubRepoIdsByUser_IdAndDeletedFalseAndGithubRepoIdIn(userId, githubIds));

        java.util.List<com.stackup.stackup.github.application.dto.CandidateRepositoryResult> content =
            listPage.repos().stream().map(r ->
                new com.stackup.stackup.github.application.dto.CandidateRepositoryResult(
                    r.id(), r.name(), r.fullName(), r.htmlUrl(), r.defaultBranch(),
                    r.privateRepo(), r.description(), registered.contains(r.id())
                )).toList();

        return new com.stackup.stackup.github.application.dto.CandidateRepositoryListResult(
            content, page, perPage, listPage.hasNext()
        );
    }
}
