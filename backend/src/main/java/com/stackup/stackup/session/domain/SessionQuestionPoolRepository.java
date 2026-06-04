package com.stackup.stackup.session.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionQuestionPoolRepository extends JpaRepository<SessionQuestionPool, Long> {

    // 다음에 쓸 일반질문(미사용 중 idx 최솟값).
    Optional<SessionQuestionPool> findFirstBySessionIdAndUsedFalseOrderByIdxAsc(Long sessionId);

    long countBySessionId(Long sessionId);

    // 중복 질문 회피용: 주어진 세션들에서 출제된 질문 텍스트(최신순).
    @Query("select p.question from SessionQuestionPool p "
        + "where p.sessionId in :sessionIds order by p.createdAt desc")
    List<String> findRecentQuestions(@Param("sessionIds") List<Long> sessionIds, Pageable pageable);
}
