package com.stackup.stackup.resume.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByUser_IdAndDeletedFalse(Long userId);

    Page<Resume> findByUser_IdAndDeletedFalse(Long userId, Pageable pageable);

    Optional<Resume> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);
}
