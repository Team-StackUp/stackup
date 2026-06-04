package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import java.util.List;

public record SessionCreateCommand(
    String title,
    String memo,
    SessionMode mode,
    List<JobCategory> jobCategories,
    Integer maxQuestions,
    Integer maxDurationMinutes,
    Integer generalQuestionCount,
    Integer maxFollowupsPerQuestion,
    List<Long> contextDocumentIds
) {
}
