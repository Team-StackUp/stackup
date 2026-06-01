package com.stackup.stackup.github.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    List<GithubRepository> findByUser_IdAndDeletedFalse(Long userId);

    Optional<GithubRepository> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);

    Optional<GithubRepository> findByUser_IdAndGithubRepoIdAndDeletedFalse(Long userId, Long githubRepoId);

    Optional<GithubRepository> findByUser_IdAndGithubRepoId(Long userId, Long githubRepoId);

    List<GithubRepository> findByUser_IdAndGithubRepoIdInAndDeletedFalse(Long userId, Set<Long> githubRepoIds);
}
