package com.stackup.stackup.session.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    List<InterviewSession> findByUser_Id(Long userId);

    Optional<InterviewSession> findByIdAndUser_Id(Long id, Long userId);
}
