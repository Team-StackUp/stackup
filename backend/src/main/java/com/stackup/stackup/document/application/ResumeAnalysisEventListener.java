package com.stackup.stackup.document.application;

import com.stackup.stackup.resume.application.event.ResumeUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// resume 도메인이 발행한 ResumeUploadedEvent 를 받아 분석 트리거.
// 도메인 간 직접 호출(resume → document)을 피하기 위한 분리. document → resume 단방향만 유지.
@Component
@RequiredArgsConstructor
public class ResumeAnalysisEventListener {

    private final AnalysisRequestService analysisRequestService;

    @EventListener
    public void on(ResumeUploadedEvent event) {
        // 동일 트랜잭션 안에서 실행. AnalysisRequestService 내부에서 다시 @TransactionalEventListener(AFTER_COMMIT)
        // 로 분기되어 트랜잭션 커밋 후 RabbitMQ 발행.
        analysisRequestService.requestResumeAnalysis(event.userId(), event.resumeId());
    }
}
