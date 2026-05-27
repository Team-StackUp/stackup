package com.stackup.stackup.document.application;

import com.stackup.stackup.github.application.event.RepositoryRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// github 도메인 RepositoryRegisteredEvent 수신 → 분석 트리거.
// github 가 document 를 직접 의존하지 않도록 분리.
@Component
@RequiredArgsConstructor
public class RepositoryAnalysisEventListener {

    private final AnalysisRequestService analysisRequestService;

    @EventListener
    public void on(RepositoryRegisteredEvent event) {
        analysisRequestService.requestRepositoryAnalysis(event.userId(), event.repositoryId());
    }
}
