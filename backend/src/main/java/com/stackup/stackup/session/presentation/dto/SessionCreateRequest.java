package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.SessionCreateCommand;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SessionCreateRequest(
    String title,
    String memo,
    @NotNull SessionMode mode,
    @NotNull InterviewType interviewType,
    @NotNull JobCategory jobCategory,
    @Min(1) @Max(30) Integer maxQuestions,
    @Min(5) @Max(180) Integer maxDurationMinutes,
    List<Long> contextDocumentIds
) {
    public SessionCreateCommand toCommand(Long userId) {
        return new SessionCreateCommand(
            userId, title, memo, mode, interviewType, jobCategory,
            maxQuestions, maxDurationMinutes,
            contextDocumentIds == null ? List.of() : contextDocumentIds
        );
    }
}
