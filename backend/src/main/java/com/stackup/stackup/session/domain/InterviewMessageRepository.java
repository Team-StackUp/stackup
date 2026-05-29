package com.stackup.stackup.session.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {

    List<InterviewMessage> findBySession_IdOrderBySequenceNumberAsc(Long sessionId);

    @Query("select coalesce(max(m.sequenceNumber), 0) from InterviewMessage m where m.session.id = :sessionId")
    int findMaxSequenceBySessionId(@Param("sessionId") Long sessionId);

    Optional<InterviewMessage> findFirstBySession_IdOrderBySequenceNumberDesc(Long sessionId);
}
