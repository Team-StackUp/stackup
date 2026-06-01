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
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionQuestionsRequesterTest {

    @Mock RabbitMessagePublisher publisher;
    @Mock RabbitMqProperties properties;
    @Mock AnalyzedDocumentRepository documentRepository;
    @Mock ObjectStorageClient storage;
    @InjectMocks SessionQuestionsRequester requester;

    @Test
    void onSessionCreated_requestsOneInitialQuestionAndKeepsSessionLimit() {
        when(properties.routingKeys()).thenReturn(mockRoutingKeys());

        requester.onSessionCreated(new SessionCreatedEvent(
            1L,
            11L,
            SessionMode.INTEGRATED,
            JobCategory.BACKEND,
            5,
            List.of()
        ));

        ArgumentCaptor<GenerateQuestionsPayload> payloadCaptor =
            ArgumentCaptor.forClass(GenerateQuestionsPayload.class);
        verify(publisher).publishToAi(eq("generate.questions"), payloadCaptor.capture(), any(MessageContext.class));

        GenerateQuestionsPayload payload = payloadCaptor.getValue();
        assertThat(payload.sessionId()).isEqualTo(11L);
        assertThat(payload.mode()).isEqualTo(SessionMode.INTEGRATED);
        assertThat(payload.jobCategory()).isEqualTo(JobCategory.BACKEND);
        assertThat(payload.documents()).isEmpty();
        assertThat(payload.initialQuestionCount()).isEqualTo(1);
        assertThat(payload.maxQuestions()).isEqualTo(5);
    }

    private RabbitMqProperties.RoutingKeyProperties mockRoutingKeys() {
        return new RabbitMqProperties.RoutingKeyProperties(
            "analyze.resume", "analyze.repository",
            "generate.questions", "generate.followup", "generate.feedback", "analyze.voice",
            "callback.analysis", "callback.questions", "callback.feedback", "callback.voice",
            "realtime.session.notify", "realtime.user.notify", "realtime.document.notify");
    }
}
