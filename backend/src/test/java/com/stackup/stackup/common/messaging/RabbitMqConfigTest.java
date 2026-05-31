package com.stackup.stackup.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class RabbitMqConfigTest {

    private final RabbitMqConfig config = new RabbitMqConfig(rabbitMqProperties());

    @Test
    void workQueue_carriesDeadLetterArguments() {
        Queue queue = config.aiAnalyzeResumeQueue();

        assertThat(queue.getName()).isEqualTo("ai.analyze.resume");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
            .containsEntry("x-dead-letter-exchange", "stackup.dlx")
            .containsEntry("x-dead-letter-routing-key", "dlq.ai.analyze.resume");
    }

    @Test
    void allWorkQueues_routeToDeadLetterExchange() {
        assertThat(config.aiAnalyzeResumeQueue().getArguments())
            .containsEntry("x-dead-letter-routing-key", "dlq.ai.analyze.resume");
        assertThat(config.aiAnalyzeRepositoryQueue().getArguments())
            .containsEntry("x-dead-letter-routing-key", "dlq.ai.analyze.repository");
        assertThat(config.aiGenerateQuestionsQueue().getArguments())
            .containsEntry("x-dead-letter-routing-key", "dlq.ai.generate.questions");
        assertThat(config.aiGenerateFollowupQueue().getArguments())
            .containsEntry("x-dead-letter-routing-key", "dlq.ai.generate.followup");
        assertThat(config.coreCallbackAnalysisQueue().getArguments())
            .containsEntry("x-dead-letter-routing-key", "dlq.core.callback.analysis");
        assertThat(config.coreCallbackQuestionsQueue().getArguments())
            .containsEntry("x-dead-letter-routing-key", "dlq.core.callback.questions");
    }

    private RabbitMqProperties rabbitMqProperties() {
        return new RabbitMqProperties(
            "core-server",
            "v1",
            new RabbitMqProperties.Message("application/json", StandardCharsets.UTF_8.name(), "x-trace-id"),
            new RabbitMqProperties.Template(true),
            new RabbitMqProperties.Exchanges(
                true,
                false,
                new RabbitMqProperties.Exchanges.Names(
                    "stackup.core-to-ai", "stackup.ai-to-core", "stackup.realtime"
                )
            ),
            new RabbitMqProperties.Queues(
                true,
                new RabbitMqProperties.Queues.Names(
                    "ai.analyze.resume",
                    "ai.analyze.repository",
                    "ai.generate.questions",
                    "ai.generate.followup",
                    "ai.generate.feedback",
                    "ai.analyze.voice",
                    "core.callback.analysis",
                    "core.callback.questions",
                    "core.callback.feedback",
                    "core.callback.voice"
                )
            ),
            new RabbitMqProperties.RoutingKeyProperties(
                "analyze.resume",
                "analyze.repository",
                "generate.questions",
                "generate.followup",
                "generate.feedback",
                "analyze.voice",
                "callback.analysis",
                "callback.questions",
                "callback.feedback",
                "callback.voice",
                "realtime.session.notify"
            ),
            new RabbitMqProperties.DeadLetter("stackup.dlx", "dlq."),
            new RabbitMqProperties.Retry(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10))
        );
    }
}
