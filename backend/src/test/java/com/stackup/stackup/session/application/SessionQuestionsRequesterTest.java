package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.GenerateQuestionsPayload;
import com.stackup.stackup.session.application.event.SessionCreatedEvent;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionQuestionPoolRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionQuestionsRequesterTest {

    @Mock RabbitMessagePublisher publisher;
    @Mock RabbitMqProperties properties;
    @Mock AnalyzedDocumentRepository documentRepository;
    @Mock ObjectStorageClient storage;
    @Mock InterviewSessionRepository sessionRepository;
    @Mock SessionQuestionPoolRepository questionPoolRepository;
    @InjectMocks SessionQuestionsRequester requester;

    @Test
    void onSessionCreated_requestsOneInitialQuestionAndKeepsSessionLimit() {
        when(properties.routingKeys()).thenReturn(mockRoutingKeys());

        requester.onSessionCreated(new SessionCreatedEvent(
            1L,
            11L,
            SessionMode.INTEGRATED,
            List.of(JobCategory.BACKEND),
            5,
            3,
            List.of()
        ));

        ArgumentCaptor<GenerateQuestionsPayload> payloadCaptor =
            ArgumentCaptor.forClass(GenerateQuestionsPayload.class);
        verify(publisher).publishToAi(eq("generate.questions"), payloadCaptor.capture(), any(MessageContext.class));

        GenerateQuestionsPayload payload = payloadCaptor.getValue();
        assertThat(payload.sessionId()).isEqualTo(11L);
        assertThat(payload.mode()).isEqualTo(SessionMode.INTEGRATED);
        assertThat(payload.jobCategories()).containsExactly(JobCategory.BACKEND);
        assertThat(payload.documents()).isEmpty();
        // generalQuestionCount(n) 만큼 생성 요청 (이벤트의 n=3).
        assertThat(payload.initialQuestionCount()).isEqualTo(3);
        assertThat(payload.maxQuestions()).isEqualTo(5);
        // dedup 비활성(기본 0) 이면 과거 질문 없이 빈 목록.
        assertThat(payload.recentQuestions()).isEmpty();
    }

    @Test
    void onSessionCreated_includesRecentQuestionsForDedup() {
        when(properties.routingKeys()).thenReturn(mockRoutingKeys());
        ReflectionTestUtils.setField(requester, "recentSessionCount", 3);
        ReflectionTestUtils.setField(requester, "maxRecentQuestions", 30);
        when(sessionRepository.findRecentSessionIds(eq(1L), eq(11L), any(Pageable.class)))
            .thenReturn(List.of(9L, 8L));
        when(questionPoolRepository.findRecentQuestions(eq(List.of(9L, 8L)), any(Pageable.class)))
            .thenReturn(List.of("이전 질문 A", "이전 질문 B"));

        requester.onSessionCreated(new SessionCreatedEvent(
            1L, 11L, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND), 5, 3, List.of()
        ));

        ArgumentCaptor<GenerateQuestionsPayload> payloadCaptor =
            ArgumentCaptor.forClass(GenerateQuestionsPayload.class);
        verify(publisher).publishToAi(eq("generate.questions"), payloadCaptor.capture(), any(MessageContext.class));
        assertThat(payloadCaptor.getValue().recentQuestions())
            .containsExactly("이전 질문 A", "이전 질문 B");
    }

    private RabbitMqProperties.RoutingKeyProperties mockRoutingKeys() {
        return new RabbitMqProperties.RoutingKeyProperties(
            "analyze.resume", "analyze.repository",
            "generate.questions", "generate.followup", "generate.feedback", "analyze.voice",
            "generate.tts",
            "callback.analysis", "callback.questions", "callback.feedback", "callback.voice",
            "callback.tts",
            "realtime.session.notify", "realtime.user.notify", "realtime.document.notify");
    }
}
