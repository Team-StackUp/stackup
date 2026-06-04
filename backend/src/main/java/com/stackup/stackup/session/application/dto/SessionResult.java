package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import java.time.Instant;
import java.util.List;

public record SessionResult(
    Long id,
    String title,
    String memo,
    SessionMode mode,
    JobCategory jobCategory,
    List<JobCategory> jobCategories,
    Integer maxQuestions,
    Integer maxDurationMinutes,
    Integer generalQuestionCount,
    Integer maxFollowupsPerQuestion,
    SessionStatus status,
    Integer totalQuestionCount,
    Instant startedAt,
    Instant endedAt,
    List<Long> contextDocumentIds,
    Instant createdAt,
    Instant updatedAt
) {
    public static SessionResult of(InterviewSession session, List<Long> documentIds) {
        return new SessionResult(
            session.getId(),
            session.getTitle(),
            session.getMemo(),
            session.getMode(),
            session.getJobCategory(),
            List.copyOf(session.getJobCategories()),
            session.getMaxQuestions(),
            session.getMaxDurationMinutes(),
            session.getGeneralQuestionCount(),
            session.getMaxFollowupsPerQuestion(),
            session.getStatus(),
            session.getTotalQuestionCount(),
            session.getStartedAt(),
            session.getEndedAt(),
            documentIds,
            session.getCreatedAt(),
            session.getUpdatedAt()
        );
    }
}
