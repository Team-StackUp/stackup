package com.stackup.stackup.resume.application.event;

// 웹 이력서(URL) 등록 직후 발행. document 도메인이 listener 로 받아 analyze.web 분석을 트리거한다.
// PDF 업로드의 ResumeUploadedEvent 와 같은 역할 — resume → document 직접 의존을 피하기 위한 매개체.
public record WebResumeRegisteredEvent(
    Long userId,
    Long resumeId
) {
}
