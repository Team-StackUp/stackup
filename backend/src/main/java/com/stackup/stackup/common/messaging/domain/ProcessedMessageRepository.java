package com.stackup.stackup.common.messaging.domain;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {

    boolean existsById(String messageId);

    // 보존 기한이 지난 멱등 레코드 정리. idx_processed_messages_processed_at 이 이 조회를
    // 위해 처음부터 있었는데(다른 어떤 쿼리도 processed_at 을 안 쓴다) 정작 지우는 쪽이 없었다.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ProcessedMessage pm WHERE pm.processedAt < :threshold")
    int deleteProcessedBefore(@Param("threshold") Instant threshold);
}
