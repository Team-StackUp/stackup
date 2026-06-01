package com.stackup.stackup.session.application.dto;

import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;

public record GenerateFollowupPayload(
    Long sessionId,
    Long parentMessageId,
    Long answerMessageId,
    String previousQuestion,
    String answerText,
    SessionMode mode,
    JobCategory jobCategory
) {
}
