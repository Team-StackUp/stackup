package com.stackup.stackup.session.application.event;

import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import java.util.List;

// 자기소개 답변 commit 후 발행. 인프라스트럭처가 받아 generate.questions 메시지를 발행한다.
// 모든 면접의 첫 질문은 자기소개로 고정이며, 이력서/레포 기반 질문 풀은 이 자기소개 답변을
// 씨앗으로 생성된다. SessionCreatedEvent 와 동일 필드 + selfIntroAnswer 를 실어 lazy 로딩을 피한다.
public record SelfIntroAnsweredEvent(
    Long userId,
    Long sessionId,
    SessionMode mode,
    List<JobCategory> jobCategories,
    Integer maxQuestions,
    Integer generalQuestionCount,
    List<Long> contextDocumentIds,
    String selfIntroAnswer,
    // 직무 맞춤 모드 전용 타깃 회사/JD. 다른 모드는 null.
    String targetCompanyName,
    String targetJobDescription
) {
}
