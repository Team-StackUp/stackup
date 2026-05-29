package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;

public record GenerateFollowupPayload(
    Long sessionId,
    Long parentMessageId,
    Long answerMessageId,
    String previousQuestion,
    String answerText,
    InterviewType interviewType,
    JobCategory jobCategory
) {
}
