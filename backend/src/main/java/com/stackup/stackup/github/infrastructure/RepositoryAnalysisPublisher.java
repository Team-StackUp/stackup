package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.retry.RetryPolicy;
import com.stackup.stackup.common.retry.RetryingExecutor;
import com.stackup.stackup.github.application.event.RepositoryRegisteredEvent;
import com.stackup.stackup.github.infrastructure.dto.AnalyzeRepositoryPayload;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RepositoryAnalysisPublisher {

    private static final RetryPolicy RETRY_POLICY = RetryPolicy.exponentialBackoff(500, 3);

    private final RabbitMessagePublisher rabbitPublisher;
    private final RabbitMqProperties properties;
    private final RetryingExecutor retryingExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RepositoryRegisteredEvent event) {
        String routingKey = properties.routingKeys().analyzeRepository();
        AnalyzeRepositoryPayload payload = new AnalyzeRepositoryPayload(
            event.repositoryId(),
            event.githubFullName(),
            event.defaultBranch(),
            event.encryptedGithubAccessToken()
        );
        Map<String, Object> context = Map.of("userId", event.userId());

        retryingExecutor.execute(
                "analyze.repository[repositoryId=" + event.repositoryId() + ",userId=" + event.userId() + "]",
            RETRY_POLICY,
            () -> rabbitPublisher.publishToAi(routingKey, payload, context)
        );
    }
}
