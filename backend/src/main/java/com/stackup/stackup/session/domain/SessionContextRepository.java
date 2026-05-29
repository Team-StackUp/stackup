package com.stackup.stackup.session.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionContextRepository extends JpaRepository<SessionContext, Long> {

    List<SessionContext> findBySession_Id(Long sessionId);
}
