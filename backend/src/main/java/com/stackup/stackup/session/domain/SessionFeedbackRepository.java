package com.stackup.stackup.session.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, Long> {

    Optional<SessionFeedback> findBySession_Id(Long sessionId);
}
