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
    List<Long> contextDocumentIds,
    // 직무 맞춤 모드 전용. 지원 회사명 + 채용공고(JD) 원문.
    String targetCompanyName,
    String targetJobDescription
) {
}
