package com.stackup.stackup.session.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionContextRepository extends JpaRepository<SessionContext, Long> {

    Optional<SessionContext> findBySession_Id(Long sessionId);
}
