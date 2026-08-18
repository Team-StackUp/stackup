package com.stackup.stackup.document.application;

import com.stackup.stackup.resume.application.event.WebResumeRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// resume 도메인이 발행한 WebResumeRegisteredEvent 를 받아 웹 분석 트리거.
// ResumeAnalysisEventListener 와 같은 패턴 — document → resume 단방향만 유지.
@Component
@RequiredArgsConstructor
public class WebResumeAnalysisEventListener {

    private final AnalysisRequestService analysisRequestService;

    @EventListener
    public void on(WebResumeRegisteredEvent event) {
        analysisRequestService.requestWebResumeAnalysis(event.userId(), event.resumeId());
    }
}
