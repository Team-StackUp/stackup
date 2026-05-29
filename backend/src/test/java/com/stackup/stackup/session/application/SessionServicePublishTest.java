package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.GenerateQuestionsPayload;
import com.stackup.stackup.session.application.event.SessionCreatedEvent;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServicePublishTest {

    @Mock InterviewSessionRepository sessionRepo;
    @Mock SessionContextRepository contextRepo;
    @Mock InterviewMessageRepository messageRepo;
    @Mock AnalyzedDocumentRepository documentRepo;
    @Mock UserRepository userRepo;
    @Mock ProcessedMessageRepository processedRepo;
    @Mock SseEventPublisher sse;
    @Mock RabbitMessagePublisher publisher;
    @Mock RabbitMqProperties properties;
    @Mock RabbitMqProperties.RoutingKeyProperties routingKeys;
    @Mock ApplicationEventPublisher events;

    @InjectMocks SessionService service;

    @Test
    void onSessionCreated_publishes_generate_questions() {
        when(properties.routingKeys()).thenReturn(routingKeys);
        when(routingKeys.generateQuestions()).thenReturn("generate.questions");

        GenerateQuestionsPayload payload = new GenerateQuestionsPayload(
                99L, "TECHNICAL", "BACKEND", List.of(11L, 12L), 10);
        service.onSessionCreated(new SessionCreatedEvent(99L, 1L, payload));

        ArgumentCaptor<MessageContext> ctx = ArgumentCaptor.forClass(MessageContext.class);
        verify(publisher).publishToAi(eq("generate.questions"), eq(payload), ctx.capture());
        assertThat(ctx.getValue().userId()).isEqualTo(1L);
        assertThat(ctx.getValue().sessionId()).isEqualTo(99L);
    }
}
