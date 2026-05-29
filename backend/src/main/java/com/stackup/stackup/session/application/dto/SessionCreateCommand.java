package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;

import java.util.List;

public record SessionCreateCommand(
        Long userId,
        String title,
        String memo,
        SessionMode mode,
        InterviewType interviewType,
        JobCategory jobCategory,
        Integer maxQuestions,
        Integer maxDurationMinutes,
        List<Long> contextDocumentIds
) {}
