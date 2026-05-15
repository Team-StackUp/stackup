package com.stackup.stackup.resume.infrastructure;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.resume.application.event.ResumeUploadedEvent;
import com.stackup.stackup.resume.infrastructure.dto.AnalyzeResumePayload;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeAnalysisPublisher {

    private final RabbitMessagePublisher rabbitPublisher;
    private final RabbitMqProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ResumeUploadedEvent event) {
        try {
            rabbitPublisher.publishToAi(
                properties.routingKeys().analyzeResume(),
                new AnalyzeResumePayload(event.resumeId(), event.s3Key()),
                Map.of("userId", event.userId())
            );
        } catch (RuntimeException publishError) {
            log.warn("Failed to publish analyze.resume for resumeId={}", event.resumeId(), publishError);
        }
    }
}
