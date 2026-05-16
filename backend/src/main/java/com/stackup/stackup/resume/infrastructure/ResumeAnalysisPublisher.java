package com.stackup.stackup.resume.infrastructure;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.retry.RetryPolicy;
import com.stackup.stackup.common.retry.RetryingExecutor;
import com.stackup.stackup.resume.application.event.ResumeUploadedEvent;
import com.stackup.stackup.resume.infrastructure.dto.AnalyzeResumePayload;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeAnalysisPublisher {

    private static final RetryPolicy RETRY_POLICY = RetryPolicy.exponentialBackoff(500, 5);

    private final RabbitMessagePublisher rabbitPublisher;
    private final RabbitMqProperties properties;
    private final RetryingExecutor retryingExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ResumeUploadedEvent event) {
        String routingKey = properties.routingKeys().analyzeResume();
        AnalyzeResumePayload payload = new AnalyzeResumePayload(event.resumeId(), event.s3Key());
        Map<String, Object> context = Map.of("userId", event.userId());

        retryingExecutor.execute(
            "analyze.resume[resumeId=" + event.resumeId() + ",userId=" + event.userId() + "]",
            RETRY_POLICY,
            () -> rabbitPublisher.publishToAi(routingKey, payload, context)
        );
    }
}
