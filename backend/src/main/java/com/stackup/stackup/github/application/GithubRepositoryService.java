package com.stackup.stackup.github.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.application.dto.CandidateRepositoryListResult;
import com.stackup.stackup.github.application.dto.CandidateRepositoryResult;
import com.stackup.stackup.github.application.dto.GithubRepositoryResult;
import com.stackup.stackup.github.application.dto.RegisterRepositoryCommand;
import com.stackup.stackup.github.application.event.RepositoryRegisteredEvent;
import com.stackup.stackup.github.domain.GithubRepository;
import com.stackup.stackup.github.domain.GithubRepositoryRepository;
import com.stackup.stackup.github.infrastructure.GithubApiClient;
import com.stackup.stackup.github.infrastructure.GithubTokenCipher;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoListPage;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GithubRepositoryService {

    private final GithubRepositoryRepository repoRepository;
    private final UserRepository userRepository;
    private final GithubApiClient githubApiClient;
    private final GithubTokenCipher tokenCipher;
    private final ApplicationEventPublisher events;


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

    @Transactional
    public GithubRepositoryResult register(Long userId, RegisterRepositoryCommand command) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));

        String plainToken = tokenCipher.decrypt(user.getEncryptedGithubAccessToken());
        GithubRepoResponse meta = githubApiClient.getRepository(plainToken, command.githubRepoId());

        GithubRepository repo = repoRepository.findByUser_IdAndGithubRepoId(userId, command.githubRepoId())
            .map(existing -> {
                if (!existing.isDeleted()) {
                    throw new DomainException(ApiErrorCode.REPO_ALREADY_REGISTERED);
                }
                existing.resurrect(meta.name(), meta.fullName(), meta.htmlUrl(), meta.defaultBranch());
                return existing;
            })
            .orElseGet(() -> repoRepository.save(GithubRepository.create(
                user,
                meta.id(), meta.name(), meta.fullName(), meta.htmlUrl(), meta.defaultBranch()
            )));

        String sealedToken = tokenCipher.encrypt(plainToken);
        events.publishEvent(new RepositoryRegisteredEvent(
            repo.getId(), userId, repo.getRepoFullName(), repo.getDefaultBranch(), sealedToken
        ));

        return GithubRepositoryResult.from(repo);
    }

    public CandidateRepositoryListResult listCandidates(Long userId, int page, int perPage) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));

        String token = tokenCipher.decrypt(user.getEncryptedGithubAccessToken());
        GithubRepoListPage listPage = githubApiClient.listUserRepositories(token, page, perPage);

        Set<Long> githubIds = listPage.repos().stream()
            .map(GithubRepoResponse::id)
            .collect(Collectors.toSet());

        Set<Long> registered = githubIds.isEmpty()
            ? Set.of()
            : new HashSet<>(repoRepository
                .findGithubRepoIdsByUser_IdAndDeletedFalseAndGithubRepoIdIn(userId, githubIds));

        List<CandidateRepositoryResult> content = listPage.repos().stream()
            .map(r -> new CandidateRepositoryResult(
                r.id(), r.name(), r.fullName(), r.htmlUrl(), r.defaultBranch(),
                r.privateRepo(), r.description(), registered.contains(r.id())
            ))
            .toList();

        return new CandidateRepositoryListResult(content, page, perPage, listPage.hasNext());
    }
}
