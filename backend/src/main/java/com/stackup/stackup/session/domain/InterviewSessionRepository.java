package com.stackup.stackup.session.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    List<InterviewSession> findByUser_Id(Long userId);

    Optional<InterviewSession> findByIdAndUser_Id(Long id, Long userId);

    Optional<InterviewSession> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);

    List<InterviewSession> findByUser_IdAndDeletedFalseOrderByIdDesc(Long userId);

    Page<InterviewSession> findByUser_IdAndDeletedFalse(Long userId, Pageable pageable);

    long countByUser_Id(Long userId);

    long countByUser_IdAndStatus(Long userId, SessionStatus status);

    List<InterviewSession> findByUser_IdAndStatusOrderByEndedAtDesc(Long userId, SessionStatus status, Pageable pageable);
}
