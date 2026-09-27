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


    /**
     * 콜백의 sessionId 와 메시지의 실제 세션이 다르면 버린다.
     *
     * <p>스트리밍 음성(RT3)은 messageId 를 클라이언트가 쿼리로 보낸다
     * (wss://…/realtime/sessions/{id}/audio?messageId=N). RealTime 은 토큰의 SESSION 범위가
     * URL 세션 id 와 맞는지만 보고 messageId 는 검사하지 않으며, AI 는 DB 를 모른다.
     * 이 검사가 없으면 자기 세션 토큰을 가진 사용자가 남의 messageId 를 실어 보내
     * <b>타인의 답변을 덮어쓸 수 있다</b>.
     */
    @Test
    void apply_dropsCallbackWhenMessageBelongsToAnotherSession() {
        InterviewSession victimSession = sessionFixture(99L);
        InterviewMessage victimQuestion = InterviewMessage.interviewer(victimSession, 1, "피해자 질문");
        ReflectionTestUtils.setField(victimQuestion, "id", 900L);
        InterviewMessage victimAnswer =
            InterviewMessage.voiceInterviewee(victimSession, 2, victimQuestion, "k-victim");
        ReflectionTestUtils.setField(victimAnswer, "id", 901L);

        // 공격자는 자기 세션(50)의 토큰으로 피해자 메시지(901) 를 지목한다.
        VoiceCallbackPayload payload = new VoiceCallbackPayload(50L, 901L,
            "공격자가 말한 내용", 120.0, 1.0, Map.of(), 0.9, null);
        VoiceCallbackEnvelope env = new VoiceCallbackEnvelope("vc-attack", "callback.voice", "1", "t",
            null, "ai", payload, null);

        when(processedMessageRepository.existsById("vc-attack")).thenReturn(false);
        when(messageRepository.findById(901L)).thenReturn(Optional.of(victimAnswer));

        service.apply(env);

        assertThat(victimAnswer.getContent()).isEqualTo(InterviewMessage.VOICE_TRANSCRIPTION_PENDING_TEXT);
        assertThat(victimAnswer.getStatus()).isEqualTo(MessageStatus.CREATED);
        verify(voiceAnalysisRepository, never()).save(any(MessageVoiceAnalysis.class));
        verify(events, never()).publishEvent(any(AnswerSubmittedEvent.class));
    }

    /**
     * 이미 확정된 메시지는 늦은 콜백으로 되살리지 않는다.
     *
     * <p>StaleTranscriptionSweeper 가 FAILED 로 확정해 턴을 푼 뒤 사용자가 다시 답했는데
     * 늦은 콜백이 옛 메시지를 COMPLETED 로 살리면 같은 턴에 답변이 둘이 된다
     * (꼬리질문 쪽에서 같은 이유로 막아 둔 것과 동일).
     */
    @Test
    void apply_dropsLateCallbackForAlreadySettledMessage() {
        InterviewSession session = sessionFixture(50L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "질문");
        ReflectionTestUtils.setField(question, "id", 100L);
        InterviewMessage swept = InterviewMessage.voiceInterviewee(session, 2, question, "k1");
        ReflectionTestUtils.setField(swept, "id", 200L);
        swept.failVoiceTranscription();   // 스위퍼가 확정

        VoiceCallbackPayload payload = new VoiceCallbackPayload(50L, 200L,
            "늦게 도착한 전사", 120.0, 1.0, Map.of(), 0.9, null);
        VoiceCallbackEnvelope env = new VoiceCallbackEnvelope("vc-late", "callback.voice", "1", "t",
            null, "ai", payload, null);

        when(processedMessageRepository.existsById("vc-late")).thenReturn(false);
        when(messageRepository.findById(200L)).thenReturn(Optional.of(swept));

        service.apply(env);

        assertThat(swept.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(swept.getContent()).isEqualTo(InterviewMessage.VOICE_TRANSCRIPTION_FAILED_TEXT);
        verify(events, never()).publishEvent(any(AnswerSubmittedEvent.class));
    }

    private InterviewSession sessionFixture(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, null, null);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}
