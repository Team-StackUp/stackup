package com.stackup.stackup.session.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, Long> {

    Optional<SessionFeedback> findBySession_Id(Long sessionId);

    boolean existsBySession_Id(Long sessionId);

    @Query("""
        SELECT f FROM SessionFeedback f
        WHERE f.session.user.id = :userId
        ORDER BY f.session.endedAt DESC NULLS LAST, f.id DESC
        """)
    List<SessionFeedback> findRecentByOwner(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT AVG(f.overallScore) FROM SessionFeedback f
        WHERE f.session.user.id = :userId
        """)
    Double averageOverallScore(@Param("userId") Long userId);

    @Query("""
        SELECT AVG(f.technicalAccuracy) FROM SessionFeedback f
        WHERE f.session.user.id = :userId
        """)
    Double averageTechnicalAccuracy(@Param("userId") Long userId);

    @Query("""
        SELECT AVG(f.logicScore) FROM SessionFeedback f
        WHERE f.session.user.id = :userId
        """)
    Double averageLogicScore(@Param("userId") Long userId);

    @Query("""
        SELECT AVG(f.communicationScore) FROM SessionFeedback f
        WHERE f.session.user.id = :userId
        """)
    Double averageCommunicationScore(@Param("userId") Long userId);
}
