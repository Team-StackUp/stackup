package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.VoiceCallbackEnvelope;
import com.stackup.stackup.session.application.dto.VoiceCallbackPayload;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.MessageStatus;
import com.stackup.stackup.session.domain.MessageVoiceAnalysis;
import com.stackup.stackup.session.domain.MessageVoiceAnalysisRepository;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.domain.User;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoiceCallbackServiceTest {

    @Mock InterviewMessageRepository messageRepository;
    @Mock MessageVoiceAnalysisRepository voiceAnalysisRepository;
    @Mock ProcessedMessageRepository processedMessageRepository;
    @Mock SseEventPublisher sseEventPublisher;
    @Mock ApplicationEventPublisher events;
    @InjectMocks VoiceCallbackService service;

    @Test
    void apply_setsTranscriptAndPublishesAnswerSubmittedEvent() {
        InterviewSession session = sessionFixture(50L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "ACID?");
        ReflectionTestUtils.setField(question, "id", 100L);
        InterviewMessage voiceMsg = InterviewMessage.voiceInterviewee(session, 2, question, "k1");
        ReflectionTestUtils.setField(voiceMsg, "id", 200L);

        VoiceCallbackPayload payload = new VoiceCallbackPayload(50L, 200L,
            "원자성 일관성 격리성 영속성",
            120.0, 1.5, Map.of("음", 2), 0.85, null);
        VoiceCallbackEnvelope env = new VoiceCallbackEnvelope("vc-1", "callback.voice", "1", "t",
            null, "ai", payload, null);

        when(processedMessageRepository.existsById("vc-1")).thenReturn(false);
        when(messageRepository.findById(200L)).thenReturn(Optional.of(voiceMsg));
        when(voiceAnalysisRepository.existsByMessage_Id(200L)).thenReturn(false);

        service.apply(env);

        assertThat(voiceMsg.getContent()).isEqualTo("원자성 일관성 격리성 영속성");
        assertThat(voiceMsg.getStatus()).isEqualTo(MessageStatus.COMPLETED);
        verify(voiceAnalysisRepository).save(any(MessageVoiceAnalysis.class));
        verify(events).publishEvent(any(AnswerSubmittedEvent.class));
        verify(sseEventPublisher).publishToSession(eq(50L), eq(SseEventType.SESSION_MESSAGE), any());
    }

    @Test
    void apply_marksMessageFailedOnErrorCode() {
        InterviewSession session = sessionFixture(50L);
        InterviewMessage voiceMsg = InterviewMessage.voiceInterviewee(session, 2, null, null);
        ReflectionTestUtils.setField(voiceMsg, "id", 200L);

        VoiceCallbackPayload payload = new VoiceCallbackPayload(50L, 200L, null, null, null,
            Map.of(), null, "STT_AUTH_FAILED");
        VoiceCallbackEnvelope env = new VoiceCallbackEnvelope("vc-fail", "callback.voice", "1", "t",
            null, "ai", payload, null);

        when(processedMessageRepository.existsById("vc-fail")).thenReturn(false);
        when(messageRepository.findById(200L)).thenReturn(Optional.of(voiceMsg));

        service.apply(env);

        assertThat(voiceMsg.getStatus()).isEqualTo(MessageStatus.FAILED);
        verify(voiceAnalysisRepository, never()).save(any(MessageVoiceAnalysis.class));
        verify(events, never()).publishEvent(any(AnswerSubmittedEvent.class));
    }

    @Test
    void apply_skipsDuplicateMessageId() {
        VoiceCallbackPayload payload = new VoiceCallbackPayload(50L, 200L, "t", null, null,
            Map.of(), null, null);
        VoiceCallbackEnvelope env = new VoiceCallbackEnvelope("dup", "callback.voice", "1", "t",
            null, "ai", payload, null);
        when(processedMessageRepository.existsById("dup")).thenReturn(true);

        service.apply(env);

        verify(messageRepository, never()).findById(any());
    }

    private InterviewSession sessionFixture(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(user, "t", null, SessionMode.ONLINE,
            InterviewType.TECHNICAL, JobCategory.BACKEND, 5, 30);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}
