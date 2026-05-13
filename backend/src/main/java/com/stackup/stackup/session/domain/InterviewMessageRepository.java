package com.stackup.stackup.session.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {

    List<InterviewMessage> findBySession_IdOrderBySequenceNumberAsc(Long sessionId);
}
