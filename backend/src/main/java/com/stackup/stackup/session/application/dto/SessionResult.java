package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;

import java.time.Instant;

public record SessionResult(
        Long id,
        Long userId,
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
    public static SessionResult from(InterviewSession s) {
        return new SessionResult(
                s.getId(), s.getUser().getId(), s.getTitle(), s.getMemo(),
                s.getMode(), s.getInterviewType(), s.getJobCategory(),
                s.getMaxQuestions(), s.getMaxDurationMinutes(),
                s.getStatus(), s.getTotalQuestionCount(),
                s.getStartedAt(), s.getEndedAt(), s.getCreatedAt()
        );
    }
}
