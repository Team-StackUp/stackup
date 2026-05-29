package com.stackup.stackup.session.application.event;

import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import java.util.List;

// 세션 생성 commit 후 발행. 인프라스트럭처가 받아 generate.questions 메시지를 RabbitMQ 에 publish.
public record SessionCreatedEvent(
    Long userId,
    Long sessionId,
    InterviewType interviewType,
    JobCategory jobCategory,
    Integer maxQuestions,
    List<Long> contextDocumentIds
) {
}
