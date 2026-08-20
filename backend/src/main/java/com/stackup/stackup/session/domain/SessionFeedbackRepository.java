package com.stackup.stackup.session.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, Long> {

    Optional<SessionFeedback> findBySession_Id(Long sessionId);

    Optional<SessionFeedback> findByShareToken(String shareToken);

    @Query("SELECT f FROM SessionFeedback f WHERE f.session.user.id = :userId AND f.shareToken IS NOT NULL")
    List<SessionFeedback> findSharedByOwner(@Param("userId") Long userId);

    boolean existsBySession_Id(Long sessionId);

    // 아래 통계 쿼리들은 모두 삭제된 세션을 뺀다. UserStatsService 의 총/완료 카운트는
    // countByUser_IdAndDeletedFalse 로 이미 빼고 있어서, 여기서 안 빼면 "세션 3개 완료"인데
    // 추이 그래프엔 점이 5개 찍히는 식으로 어긋난다. 무엇보다 사용자가 '기록 삭제'로 기대하는
    // 것은 그 세션이 통계에서도 사라지는 것이다(getByToken 이 공유 링크에 같은 원칙을 적용한다).
    @Query("""
        SELECT f FROM SessionFeedback f
        WHERE f.session.user.id = :userId
          AND f.deleted = false
          AND f.session.deleted = false
        ORDER BY f.session.endedAt DESC NULLS LAST, f.id DESC
        """)
    List<SessionFeedback> findRecentByOwner(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT AVG(f.overallScore) FROM SessionFeedback f
        WHERE f.session.user.id = :userId
          AND f.deleted = false
          AND f.session.deleted = false
        """)
    Double averageOverallScore(@Param("userId") Long userId);

    @Query("""
        SELECT AVG(f.technicalAccuracy) FROM SessionFeedback f
        WHERE f.session.user.id = :userId
          AND f.deleted = false
          AND f.session.deleted = false
        """)
    Double averageTechnicalAccuracy(@Param("userId") Long userId);

    @Query("""
        SELECT AVG(f.logicScore) FROM SessionFeedback f
        WHERE f.session.user.id = :userId
          AND f.deleted = false
          AND f.session.deleted = false
        """)
    Double averageLogicScore(@Param("userId") Long userId);

    @Query("""
        SELECT AVG(f.communicationScore) FROM SessionFeedback f
        WHERE f.session.user.id = :userId
          AND f.deleted = false
          AND f.session.deleted = false
        """)
    Double averageCommunicationScore(@Param("userId") Long userId);
}
