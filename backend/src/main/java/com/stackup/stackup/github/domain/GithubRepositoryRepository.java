package com.stackup.stackup.github.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    List<GithubRepository> findByUser_IdAndDeletedFalse(Long userId);

    Optional<GithubRepository> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);
}
