package com.stackup.stackup.log.activity.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findTop100ByUser_IdOrderByCreatedAtDesc(Long userId);
}
