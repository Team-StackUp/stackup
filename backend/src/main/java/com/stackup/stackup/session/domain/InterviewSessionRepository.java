package com.stackup.stackup.session.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    // 중복 질문 회피용: 유저의 최근 세션 id (현재 세션 제외, 최신순).
    @Query("select s.id from InterviewSession s "
        + "where s.user.id = :userId and s.id <> :excludeId and s.deleted = false "
        + "order by s.createdAt desc")
    List<Long> findRecentSessionIds(@Param("userId") Long userId,
                                    @Param("excludeId") Long excludeId,
                                    Pageable pageable);

    List<InterviewSession> findByUser_Id(Long userId);

    Optional<InterviewSession> findByIdAndUser_Id(Long id, Long userId);

    Optional<InterviewSession> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);

    List<InterviewSession> findByUser_IdAndDeletedFalseOrderByIdDesc(Long userId);

    Page<InterviewSession> findByUser_IdAndDeletedFalse(Long userId, Pageable pageable);

    long countByUser_IdAndDeletedFalse(Long userId);

    long countByUser_IdAndStatusAndDeletedFalse(Long userId, SessionStatus status);

    // 자동 종료 스위퍼용: 진행 중(또는 특정 상태) 세션 전체. 진행 세션은 소수라 메모리 필터로 충분.
    List<InterviewSession> findByStatusAndDeletedFalse(SessionStatus status);

    List<InterviewSession> findByUser_IdAndStatusOrderByEndedAtDesc(Long userId, SessionStatus status, Pageable pageable);

    // 원자적 종료 전이: IN_PROGRESS 일 때만 단일 조건부 UPDATE 로 종료 상태로 바꾼다.
    // 영향 행 수(0 또는 1)를 반환 — 1을 받은 호출자만 '종료를 차지(claim)'했으므로
    // SessionEndedEvent 등 종료 부수효과를 단 한 번만 발행한다. 동시 종료(스위퍼·수동·콜백)
    // 시 DB 행 락으로 직렬화돼 정확히 하나만 1을 받는다(중복 피드백 발행 방지).
    @Modifying
    @Query("update InterviewSession s set s.status = :to, s.endedAt = :now "
        + "where s.id = :id and s.status = com.stackup.stackup.session.domain.SessionStatus.IN_PROGRESS")
    int finishIfInProgress(@Param("id") Long id,
                           @Param("to") SessionStatus to,
                           @Param("now") Instant now);
}
