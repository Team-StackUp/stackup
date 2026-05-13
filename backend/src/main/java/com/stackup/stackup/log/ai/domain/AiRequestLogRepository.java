package com.stackup.stackup.log.ai.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {

    List<AiRequestLog> findTop100ByUser_IdOrderByCreatedAtDesc(Long userId);
}
