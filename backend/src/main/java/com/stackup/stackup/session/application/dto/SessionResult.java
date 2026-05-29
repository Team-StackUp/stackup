package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewType;
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
    InterviewType interviewType,
    JobCategory jobCategory,
    Integer maxQuestions,
    Integer maxDurationMinutes,
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
            session.getInterviewType(),
            session.getJobCategory(),
            session.getMaxQuestions(),
            session.getMaxDurationMinutes(),
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
