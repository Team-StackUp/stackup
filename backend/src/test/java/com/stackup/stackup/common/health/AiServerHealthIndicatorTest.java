package com.stackup.stackup.common.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * AI 생존을 HTTP 가 아니라 큐 컨슈머 수로 판단한다 — Core→AI HTTP 의존을 만들지 않기 위해서다
 * (아키텍처 §4.1). 컨슈머 0 은 "프로세스는 떠 있지만 일을 안 받는" 상태까지 잡아낸다.
 */
@ExtendWith(MockitoExtension.class)
class AiServerHealthIndicatorTest {

    private static final String QUEUE = "ai.generate.questions";

    @Mock AmqpAdmin amqpAdmin;

    AiServerHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new AiServerHealthIndicator(amqpAdmin, propertiesWithQueue(QUEUE));
    }

    @Test
    void up_whenQueueHasConsumers() {
        when(amqpAdmin.getQueueInfo(QUEUE)).thenReturn(new QueueInformation(QUEUE, 3, 2));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("consumers", 2);
        assertThat(health.getDetails()).containsEntry("pendingMessages", 3L);
    }

    // 큐는 있는데 아무도 안 먹고 있으면 면접이 시작되지 않는다 — UP 으로 볼 수 없다.
    @Test
    void down_whenNoConsumers() {
        when(amqpAdmin.getQueueInfo(QUEUE)).thenReturn(new QueueInformation(QUEUE, 12, 0));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("consumers", 0);
        // 쌓인 메시지 수가 함께 보여야 얼마나 밀렸는지 판단할 수 있다.
        assertThat(health.getDetails()).containsEntry("pendingMessages", 12L);
    }

    @Test
    void down_whenQueueMissing() {
        when(amqpAdmin.getQueueInfo(QUEUE)).thenReturn(null);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    // 브로커가 죽은 경우는 rabbitmq 컴포넌트가 따로 알려준다. 여기서 DOWN 을 겹쳐 내면
    // "AI 가 죽었다" 로 오독된다 — 판단 불가로 남긴다.
    @Test
    void unknown_whenBrokerUnreachable() {
        when(amqpAdmin.getQueueInfo(QUEUE)).thenThrow(new IllegalStateException("connection refused"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("queue", QUEUE);
    }

    private RabbitMqProperties propertiesWithQueue(String generateQuestions) {
        return new RabbitMqProperties(
            "core", "1",
            new RabbitMqProperties.Message("application/json", "UTF-8", "X-Trace-Id"),
            new RabbitMqProperties.Template(true),
            new RabbitMqProperties.Exchanges(true, false,
                new RabbitMqProperties.Exchanges.Names("core.ai", "ai.core", "realtime")),
            new RabbitMqProperties.Queues(true,
                new RabbitMqProperties.Queues.Names(
                    "ai.analyze.resume", "ai.analyze.repository", "ai.analyze.web",
                    "ai.analyze.cover_letter", generateQuestions, "ai.generate.followup",
                    "ai.generate.feedback", "ai.analyze.voice", "ai.generate.tts",
                    "core.callback.analysis", "core.callback.questions", "core.callback.feedback",
                    "core.callback.voice", "core.callback.tts")),
            new RabbitMqProperties.RoutingKeyProperties(
                "analyze.resume", "analyze.repository", "analyze.web", "analyze.cover_letter",
                "generate.questions", "generate.followup", "generate.feedback", "analyze.voice",
                "generate.tts", "callback.analysis", "callback.questions", "callback.feedback",
                "callback.voice", "callback.tts", "session.notify", "realtime.user.notify",
                "realtime.document.notify"),
            new RabbitMqProperties.DeadLetter("dlx", "dlq."),
            new RabbitMqProperties.Retry(3, java.time.Duration.ofSeconds(1), 2.0,
                java.time.Duration.ofSeconds(10))
        );
    }
}
