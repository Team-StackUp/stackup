package com.stackup.stackup.common.health;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * AI 서버 생존 여부 — **작업 큐의 컨슈머 수**로 판단한다.
 *
 * <p>Core 는 AI 서버를 HTTP 로 호출하지 않는다(아키텍처 §4.1: RabbitMQ 경유). 헬스체크 하나
 * 때문에 Core→AI HTTP 의존을 새로 만들 이유가 없고, 컨슈머 수는 오히려 더 정확한 신호다 —
 * 프로세스가 살아 있는 것보다 <b>큐를 실제로 구독하고 있는지</b>가 중요하다.
 * 컨슈머가 0이면 질문 생성·꼬리질문·피드백이 전부 큐에 쌓이기만 한다.
 *
 * <p>빈 이름이 곧 Actuator 컴포넌트 키다 — {@code aiServerHealthIndicator} → {@code "aiServer"}.
 */
@Component
public class AiServerHealthIndicator implements HealthIndicator {

    private final AmqpAdmin amqpAdmin;
    private final RabbitMqProperties properties;

    public AiServerHealthIndicator(AmqpAdmin amqpAdmin, RabbitMqProperties properties) {
        this.amqpAdmin = amqpAdmin;
        this.properties = properties;
    }

    @Override
    public Health health() {
        // 대표 큐 하나로 판단한다. 이 큐에 컨슈머가 없으면 면접 자체가 시작되지 않는다.
        String queue = properties.queues().names().aiGenerateQuestions();
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(queue);
            if (info == null) {
                return Health.down()
                    .withDetail("queue", queue)
                    .withDetail("reason", "queue not found")
                    .build();
            }
            int consumers = info.getConsumerCount();
            Health.Builder builder = consumers > 0 ? Health.up() : Health.down();
            return builder
                .withDetail("queue", queue)
                .withDetail("consumers", consumers)
                .withDetail("pendingMessages", info.getMessageCount())
                .build();
        } catch (RuntimeException e) {
            // 브로커 자체가 죽었으면 rabbitmq 컴포넌트가 따로 알려준다. 여기선 판단 불가로 둔다.
            return Health.unknown()
                .withDetail("queue", queue)
                .withDetail("reason", e.getMessage())
                .build();
        }
    }
}
