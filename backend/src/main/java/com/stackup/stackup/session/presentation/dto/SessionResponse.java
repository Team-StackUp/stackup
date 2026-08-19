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
    // 약점 집중 재도전의 겨냥 축(TECHNICAL|LOGIC|COMMUNICATION). 일반 면접은 빈 목록.
    List<String> focusAreas,
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
            r.focusAreas(),
            r.createdAt(),
            r.updatedAt()
        );
    }
}
