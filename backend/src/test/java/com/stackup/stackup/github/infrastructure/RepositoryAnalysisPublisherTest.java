package com.stackup.stackup.github.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.retry.RetryingExecutor;
import com.stackup.stackup.github.application.event.RepositoryRegisteredEvent;
import com.stackup.stackup.github.infrastructure.dto.AnalyzeRepositoryPayload;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RepositoryAnalysisPublisherTest {

    @Test
    void delegates_to_rabbit_message_publisher_with_analyze_repository_routing_key() {
        RabbitMessagePublisher rabbit = Mockito.mock(RabbitMessagePublisher.class);
        RabbitMqProperties props = Mockito.mock(RabbitMqProperties.class, Mockito.RETURNS_DEEP_STUBS);
        when(props.routingKeys().analyzeRepository()).thenReturn("analyze.repository");

        RepositoryAnalysisPublisher publisher = new RepositoryAnalysisPublisher(rabbit, props, new RetryingExecutor());

        publisher.handle(new RepositoryRegisteredEvent(7L, 42L, "u/r", "main", "sealed"));

        ArgumentCaptor<String> rk = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Map> ctx = ArgumentCaptor.forClass(Map.class);

        verify(rabbit).publishToAi(rk.capture(), payload.capture(), ctx.capture());

        assertThat(rk.getValue()).isEqualTo("analyze.repository");
        assertThat(payload.getValue()).isInstanceOf(AnalyzeRepositoryPayload.class);
        AnalyzeRepositoryPayload p = (AnalyzeRepositoryPayload) payload.getValue();
        assertThat(p.repositoryId()).isEqualTo(7L);
        assertThat(p.githubFullName()).isEqualTo("u/r");
        assertThat(p.defaultBranch()).isEqualTo("main");
        assertThat(p.githubAccessTokenEncrypted()).isEqualTo("sealed");
        assertThat(ctx.getValue()).containsEntry("userId", 42L);
    }
}
