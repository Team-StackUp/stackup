package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.session.application.dto.AnalyzeVoicePayload;
import com.stackup.stackup.session.application.event.VoiceAnswerUploadedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceAnalysisRequesterTest {

    @Mock RabbitMessagePublisher publisher;
    @Mock RabbitMqProperties properties;
    @Mock RabbitMqProperties.RoutingKeyProperties routingKeys;
    @Mock InterviewMessageRepository messageRepository;
    @InjectMocks VoiceAnalysisRequester requester;

    @Test
    void publishesAnalyzeVoice_afterCommit() {
        InterviewSession session = Mockito.mock(InterviewSession.class);
        when(session.getId()).thenReturn(10L);
        when(session.getMode()).thenReturn(SessionMode.TECHNICAL);
        when(session.getJobCategory()).thenReturn(JobCategory.BACKEND);
        InterviewMessage parent = Mockito.mock(InterviewMessage.class);
        when(parent.getId()).thenReturn(100L);
        when(parent.getContent()).thenReturn("Tell me about ACID.");
        InterviewMessage message = Mockito.mock(InterviewMessage.class);
        when(message.getId()).thenReturn(200L);
        when(message.getSession()).thenReturn(session);
        when(message.getParentMessage()).thenReturn(parent);
        when(messageRepository.findById(200L)).thenReturn(Optional.of(message));
        when(properties.routingKeys()).thenReturn(routingKeys);
        when(routingKeys.analyzeVoice()).thenReturn("analyze.voice");

        requester.onVoiceAnswerUploaded(new VoiceAnswerUploadedEvent(
            1L, 10L, 200L, "interview/voice/raw/10/200.webm", "audio/webm"));

        ArgumentCaptor<AnalyzeVoicePayload> captor = ArgumentCaptor.forClass(AnalyzeVoicePayload.class);
        verify(publisher).publishToAi(eq("analyze.voice"), captor.capture(), any(MessageContext.class));
        AnalyzeVoicePayload payload = captor.getValue();
        assertThat(payload.sessionId()).isEqualTo(10L);
        assertThat(payload.messageId()).isEqualTo(200L);
        assertThat(payload.parentQuestionMessageId()).isEqualTo(100L);
        assertThat(payload.audioS3Key()).isEqualTo("interview/voice/raw/10/200.webm");
        assertThat(payload.contentType()).isEqualTo("audio/webm");
        assertThat(payload.previousQuestionText()).isEqualTo("Tell me about ACID.");
        assertThat(payload.mode()).isEqualTo("TECHNICAL");
        assertThat(payload.jobCategory()).isEqualTo("BACKEND");
    }

    @Test
    void skips_whenMessageNotFound() {
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        requester.onVoiceAnswerUploaded(new VoiceAnswerUploadedEvent(
            1L, 10L, 999L, "k", "audio/webm"));

        verify(publisher, never()).publishToAi(any(), any(), any());
    }
}
