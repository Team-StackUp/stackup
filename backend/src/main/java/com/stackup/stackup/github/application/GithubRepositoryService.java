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
}
