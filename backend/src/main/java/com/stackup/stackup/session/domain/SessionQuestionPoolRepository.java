package com.stackup.stackup.session.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionQuestionPoolRepository extends JpaRepository<SessionQuestionPool, Long> {

    // 다음에 쓸 일반질문(미사용 중 idx 최솟값).
    Optional<SessionQuestionPool> findFirstBySessionIdAndUsedFalseOrderByIdxAsc(Long sessionId);

    long countBySessionId(Long sessionId);
}
