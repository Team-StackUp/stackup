package com.stackup.stackup.log.ai.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {

    List<AiRequestLog> findTop100ByUser_IdOrderByCreatedAtDesc(Long userId);

    // 보존 기한이 지난 호출 로그 정리. LLM 호출마다 한 행이라 이 코드베이스에서 가장 빨리
    // 자라는 테이블인데(피드백 한 번에 10여 건 + TTS 문장마다) 지우는 쪽이 없었다.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AiRequestLog l WHERE l.createdAt < :threshold")
    int deleteCreatedBefore(@Param("threshold") Instant threshold);
}
