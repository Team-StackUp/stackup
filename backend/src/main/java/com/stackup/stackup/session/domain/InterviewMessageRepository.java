package com.stackup.stackup.session.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {

    List<InterviewMessage> findBySession_IdOrderBySequenceNumberAsc(Long sessionId);

    Optional<InterviewMessage> findFirstBySession_IdOrderBySequenceNumberDesc(Long sessionId);

    long countBySession_Id(Long sessionId);
}
