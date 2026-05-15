package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.github.application.event.RepositoryRegisteredEvent;
import com.stackup.stackup.github.infrastructure.dto.AnalyzeRepositoryPayload;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RepositoryAnalysisPublisher {

    private static final Logger log = LoggerFactory.getLogger(RepositoryAnalysisPublisher.class);

    private final RabbitMessagePublisher rabbitPublisher;
    private final RabbitMqProperties properties;

    public RepositoryAnalysisPublisher(RabbitMessagePublisher rabbitPublisher, RabbitMqProperties properties) {
        this.rabbitPublisher = rabbitPublisher;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RepositoryRegisteredEvent event) {
        try {
            rabbitPublisher.publishToAi(
                properties.routingKeys().analyzeRepository(),
                new AnalyzeRepositoryPayload(
                    event.repositoryId(),
                    event.githubFullName(),
                    event.defaultBranch(),
                    event.encryptedGithubAccessToken()
                ),
                Map.of("userId", event.userId())
            );
        } catch (RuntimeException publishError) {
            log.warn("Failed to publish analyze.repository for repositoryId={}", event.repositoryId(), publishError);
        }
    }
}
