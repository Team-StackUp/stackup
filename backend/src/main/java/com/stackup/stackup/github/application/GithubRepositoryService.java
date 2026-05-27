package com.stackup.stackup.github.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.application.dto.CandidateRepositoryResult;
import com.stackup.stackup.github.application.dto.GithubRepositoryResult;
import com.stackup.stackup.github.application.dto.RegisterRepositoryCommand;
import com.stackup.stackup.github.application.event.RepositoryRegisteredEvent;
import com.stackup.stackup.github.domain.GithubRepository;
import com.stackup.stackup.github.domain.GithubRepositoryRepository;
import com.stackup.stackup.github.infrastructure.GithubApiClient;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoResponse;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GithubRepositoryService {

    private static final int CANDIDATE_DEFAULT_PER_PAGE = 30;
    private static final int CANDIDATE_MAX_PER_PAGE = 100;

    private final GithubRepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final InternalGithubTokenService tokenService;
    private final GithubApiClient githubApiClient;
    private final ApplicationEventPublisher events;

    @Transactional
    public GithubRepositoryResult register(Long userId, RegisterRepositoryCommand command) {
        validate(command);

        User user = userRepository.findByIdAndDeletedFalse(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));

        if (repositoryRepository.findByUser_IdAndGithubRepoIdAndDeletedFalse(userId, command.githubRepoId()).isPresent()) {
            throw new DomainException(ApiErrorCode.REPO_ALREADY_REGISTERED);
        }

        // GitHub API 로 메타 재확인 (사용자 토큰 권한 검증 겸 최신 default_branch 확보)
        String accessToken = tokenService.fetchPlainAccessToken(userId);
        GithubRepoResponse meta = githubApiClient.getRepository(accessToken, command.fullName());

        if (meta.id() == null || !meta.id().equals(command.githubRepoId())) {
            // 클라이언트가 보낸 id 와 fullName 이 GitHub 상에서 불일치 — 조작/오래된 캐시
            throw new DomainException(ApiErrorCode.VALIDATION_ERROR);
        }

        GithubRepository repo = repositoryRepository.save(GithubRepository.create(
            user,
            meta.id(),
            meta.name(),
            meta.fullName(),
            meta.htmlUrl(),
            meta.defaultBranch()
        ));

        events.publishEvent(new RepositoryRegisteredEvent(userId, repo.getId()));
        return GithubRepositoryResult.of(repo);
    }

    public List<GithubRepositoryResult> listRegistered(Long userId) {
        return repositoryRepository.findByUser_IdAndDeletedFalse(userId).stream()
            .map(GithubRepositoryResult::of)
            .toList();
    }

    public GithubRepositoryResult get(Long userId, Long repositoryId) {
        return GithubRepositoryResult.of(loadOwned(userId, repositoryId));
    }

    @Transactional
    public void delete(Long userId, Long repositoryId) {
        GithubRepository repo = loadOwned(userId, repositoryId);
        repositoryRepository.delete(repo);
    }

    public List<CandidateRepositoryResult> listCandidates(Long userId, int page, int perPage) {
        int actualPerPage = (perPage <= 0) ? CANDIDATE_DEFAULT_PER_PAGE : Math.min(perPage, CANDIDATE_MAX_PER_PAGE);
        int actualPage = Math.max(page, 1);

        String accessToken = tokenService.fetchPlainAccessToken(userId);
        List<GithubRepoResponse> ghRepos = githubApiClient.listUserRepositories(accessToken, actualPage, actualPerPage);
        if (ghRepos.isEmpty()) {
            return List.of();
        }

        Set<Long> ghRepoIds = new HashSet<>();
        for (GithubRepoResponse r : ghRepos) {
            if (r.id() != null) {
                ghRepoIds.add(r.id());
            }
        }
        Set<Long> registeredIds = new HashSet<>();
        for (GithubRepository r : repositoryRepository.findByUser_IdAndGithubRepoIdInAndDeletedFalse(userId, ghRepoIds)) {
            registeredIds.add(r.getGithubRepoId());
        }

        return ghRepos.stream()
            .filter(r -> r.id() != null)
            .map(r -> new CandidateRepositoryResult(
                r.id(),
                r.name(),
                r.fullName(),
                r.htmlUrl(),
                r.defaultBranch(),
                Boolean.TRUE.equals(r.isPrivate()),
                registeredIds.contains(r.id())
            ))
            .toList();
    }

    private GithubRepository loadOwned(Long userId, Long repositoryId) {
        return repositoryRepository.findByIdAndUser_IdAndDeletedFalse(repositoryId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.REPO_NOT_FOUND));
    }

    private void validate(RegisterRepositoryCommand command) {
        if (command == null || command.githubRepoId() == null
            || command.fullName() == null || command.fullName().isBlank()
            || !command.fullName().contains("/")) {
            throw new DomainException(ApiErrorCode.VALIDATION_ERROR);
        }
    }
}
