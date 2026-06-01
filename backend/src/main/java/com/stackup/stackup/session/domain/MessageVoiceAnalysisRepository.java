package com.stackup.stackup.session.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageVoiceAnalysisRepository extends JpaRepository<MessageVoiceAnalysis, Long> {

    Optional<MessageVoiceAnalysis> findByMessage_Id(Long messageId);

    boolean existsByMessage_Id(Long messageId);

    List<MessageVoiceAnalysis> findByMessage_Session_Id(Long sessionId);
}
