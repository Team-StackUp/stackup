package com.stackup.stackup.session.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    long countByUser_Id(Long userId);

    long countByUser_IdAndStatus(Long userId, SessionStatus status);

    List<InterviewSession> findByUser_IdAndStatusOrderByEndedAtDesc(Long userId, SessionStatus status, Pageable pageable);
}
