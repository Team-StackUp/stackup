package com.stackup.stackup.session.application.event;

import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import java.util.List;

// 세션 생성 commit 후 발행. 인프라스트럭처가 받아 generate.questions 메시지를 RabbitMQ 에 publish.
public record SessionCreatedEvent(
    Long userId,
    Long sessionId,
    SessionMode mode,
    List<JobCategory> jobCategories,
    Integer maxQuestions,
    Integer generalQuestionCount,
    List<Long> contextDocumentIds
) {
}
