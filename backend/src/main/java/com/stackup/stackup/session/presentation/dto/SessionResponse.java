package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import java.time.Instant;
import java.util.List;

public record SessionResponse(
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
    String targetCompanyName,
    String targetJobDescription,
    Instant createdAt,
    Instant updatedAt
) {
    public static SessionResponse from(SessionResult r) {
        return new SessionResponse(
            r.id(),
            r.title(),
            r.memo(),
            r.mode(),
            r.jobCategory(),
            r.jobCategories(),
            r.maxQuestions(),
            r.maxDurationMinutes(),
            r.generalQuestionCount(),
            r.maxFollowupsPerQuestion(),
            r.status(),
            r.totalQuestionCount(),
            r.startedAt(),
            r.endedAt(),
            r.contextDocumentIds(),
            r.targetCompanyName(),
            r.targetJobDescription(),
            r.createdAt(),
            r.updatedAt()
        );
    }
}
