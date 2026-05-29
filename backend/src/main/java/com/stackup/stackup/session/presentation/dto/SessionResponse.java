package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;

import java.time.Instant;

public record SessionResponse(
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
    Instant createdAt
) {
    public static SessionResponse from(SessionResult r) {
        return new SessionResponse(
            r.id(), r.title(), r.memo(), r.mode(), r.interviewType(), r.jobCategory(),
            r.maxQuestions(), r.maxDurationMinutes(), r.status(), r.totalQuestionCount(),
            r.startedAt(), r.endedAt(), r.createdAt()
        );
    }
}
