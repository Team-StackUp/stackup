package com.stackup.stackup.document.application;

import com.stackup.stackup.coverletter.application.event.CoverLetterUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// coverletter 도메인이 발행한 CoverLetterUploadedEvent 를 받아 분석 트리거.
// 도메인 간 직접 호출(coverletter → document)을 피하기 위한 분리. document → coverletter 단방향만 유지.
@Component
@RequiredArgsConstructor
public class CoverLetterAnalysisEventListener {

    private final AnalysisRequestService analysisRequestService;

    @EventListener
    public void on(CoverLetterUploadedEvent event) {
        // 동일 트랜잭션 안에서 실행. AnalysisRequestService 내부에서 다시 @TransactionalEventListener(AFTER_COMMIT)
        // 로 분기되어 트랜잭션 커밋 후 RabbitMQ 발행.
        analysisRequestService.requestCoverLetterAnalysis(event.userId(), event.coverLetterId());
    }
}
