package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.VoiceCallbackEnvelope;
import com.stackup.stackup.session.application.dto.VoiceCallbackPayload;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
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
import org.mockito.ArgumentCaptor;
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
            "Transactions provide isolation and consistency",
            120.0, 1.5, Map.of("um", 2), 0.85, null);
        VoiceCallbackEnvelope env = new VoiceCallbackEnvelope("vc-1", "callback.voice", "1", "t",
            null, "ai", payload, null);

        when(processedMessageRepository.existsById("vc-1")).thenReturn(false);
        when(messageRepository.findById(200L)).thenReturn(Optional.of(voiceMsg));
        when(voiceAnalysisRepository.existsByMessage_Id(200L)).thenReturn(false);

        service.apply(env);

        assertThat(voiceMsg.getContent()).isEqualTo("Transactions provide isolation and consistency");
        assertThat(voiceMsg.getStatus()).isEqualTo(MessageStatus.COMPLETED);
        verify(voiceAnalysisRepository).save(any(MessageVoiceAnalysis.class));
        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues()).anySatisfy(e -> {
            assertThat(e).isInstanceOf(AnswerSubmittedEvent.class);
        });
        assertThat(ev.getAllValues()).anySatisfy(e -> {
            assertThat(e).isInstanceOf(RealtimeNotifyEvent.class);
            RealtimeNotifyEvent rne = (RealtimeNotifyEvent) e;
            assertThat(rne.channel()).isEqualTo(RealtimeNotifyEvent.Channel.SESSION);
            assertThat(rne.type()).isEqualTo(SseEventType.SESSION_MESSAGE);
        });
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
        assertThat(voiceMsg.getContent()).isEqualTo(InterviewMessage.VOICE_TRANSCRIPTION_FAILED_TEXT);
        verify(voiceAnalysisRepository, never()).save(any(MessageVoiceAnalysis.class));
        verify(events, never()).publishEvent(any(AnswerSubmittedEvent.class));
        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues()).anySatisfy(e -> {
            assertThat(e).isInstanceOf(RealtimeNotifyEvent.class);
            RealtimeNotifyEvent rne = (RealtimeNotifyEvent) e;
            assertThat(rne.channel()).isEqualTo(RealtimeNotifyEvent.Channel.SESSION);
            assertThat(rne.type()).isEqualTo(SseEventType.SESSION_MESSAGE);
            assertThat(rne.payload()).isInstanceOf(VoiceCallbackService.VoiceFailedNotice.class);
        });
    }

    @Test
    void apply_marksMessageFailedOnBlankTranscript() {
        InterviewSession session = sessionFixture(50L);
        InterviewMessage voiceMsg = InterviewMessage.voiceInterviewee(session, 2, null, null);
        ReflectionTestUtils.setField(voiceMsg, "id", 200L);

        VoiceCallbackPayload payload = new VoiceCallbackPayload(50L, 200L, " ", 120.0, 1.0,
            Map.of(), 0.8, null);
        VoiceCallbackEnvelope env = new VoiceCallbackEnvelope("vc-blank", "callback.voice", "1", "t",
            null, "ai", payload, null);

        when(processedMessageRepository.existsById("vc-blank")).thenReturn(false);
        when(messageRepository.findById(200L)).thenReturn(Optional.of(voiceMsg));

        service.apply(env);

        assertThat(voiceMsg.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(voiceMsg.getContent()).isEqualTo(InterviewMessage.VOICE_TRANSCRIPTION_FAILED_TEXT);
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
        InterviewSession s = InterviewSession.create(user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}
