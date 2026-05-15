package com.stackup.stackup.github.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    Page<GithubRepository> findByUser_IdAndDeletedFalse(Long userId, Pageable pageable);

    Optional<GithubRepository> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);

    Optional<GithubRepository> findByUser_IdAndGithubRepoId(Long userId, Long githubRepoId);

    @Query("""
        select g.githubRepoId from GithubRepository g
        where g.user.id = :userId and g.deleted = false and g.githubRepoId in :githubRepoIds
        """)
    List<Long> findGithubRepoIdsByUser_IdAndDeletedFalseAndGithubRepoIdIn(
        @Param("userId") Long userId,
        @Param("githubRepoIds") Collection<Long> githubRepoIds
    );
}
