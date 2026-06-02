package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.TtsCallbackEnvelope;
import com.stackup.stackup.session.application.dto.TtsCallbackPayload;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.TtsStatus;
import com.stackup.stackup.user.domain.User;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TtsCallbackServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock InterviewMessageRepository messageRepository;
    @Mock ProcessedMessageRepository processedMessageRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks TtsCallbackService service;

    @Test
    void apply_successStoresTtsFieldsWithoutChangingMessageStatus() {
        InterviewSession session = sessionFixture(50L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "Q?");
        ReflectionTestUtils.setField(question, "id", 100L);

        when(processedMessageRepository.existsById("tts-1")).thenReturn(false);
        when(messageRepository.findById(100L)).thenReturn(Optional.of(question));

        service.apply(envelope("tts-1", new TtsCallbackPayload(
            50L, 100L, "SUCCEEDED", "interview/tts/50/100.mp3", 2.4, null
        )));

        assertThat(question.getTtsStatus()).isEqualTo(TtsStatus.SUCCEEDED);
        assertThat(question.getTtsAudioPath()).isEqualTo("interview/tts/50/100.mp3");
        assertThat(question.getTtsDurationSec()).isEqualTo(2.4);
        assertThat(MessageResult.of(question).ttsAudioPath()).isEqualTo("interview/tts/50/100.mp3");
        verify(events).publishEvent(argThatObject(event ->
            isNotice(event, RealtimeNotifyEvent.Channel.SESSION, 50L)));
        verify(events).publishEvent(argThatObject(event ->
            isNotice(event, RealtimeNotifyEvent.Channel.USER, 1L)));
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }

    @Test
    void apply_failedMarksOnlyTtsFailedAndLeavesQuestionUsable() {
        InterviewSession session = sessionFixture(50L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "Q?");
        ReflectionTestUtils.setField(question, "id", 100L);

        when(processedMessageRepository.existsById("tts-fail")).thenReturn(false);
        when(messageRepository.findById(100L)).thenReturn(Optional.of(question));

        service.apply(envelope("tts-fail", new TtsCallbackPayload(
            50L, 100L, "FAILED", null, null, "TTS_PROVIDER_DOWN"
        )));

        assertThat(question.getTtsStatus()).isEqualTo(TtsStatus.FAILED);
        assertThat(question.getContent()).isEqualTo("Q?");
        assertThat(question.getTtsAudioPath()).isNull();
        verify(events).publishEvent(argThatObject(event ->
            isNotice(event, RealtimeNotifyEvent.Channel.SESSION, 50L)));
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }

    @Test
    void apply_ignoresIntervieweeMessages() {
        InterviewSession session = sessionFixture(50L);
        InterviewMessage answer = InterviewMessage.interviewee(session, 2, "A", null, null);
        ReflectionTestUtils.setField(answer, "id", 200L);

        when(processedMessageRepository.existsById("tts-answer")).thenReturn(false);
        when(messageRepository.findById(200L)).thenReturn(Optional.of(answer));

        service.apply(envelope("tts-answer", new TtsCallbackPayload(
            50L, 200L, "SUCCEEDED", "interview/tts/50/200.mp3", 1.1, null
        )));

        assertThat(answer.getTtsStatus()).isEqualTo(TtsStatus.NOT_REQUESTED);
        verify(events, never()).publishEvent(any());
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }

    @Test
    void apply_skipsDuplicateMessageId() {
        when(processedMessageRepository.existsById("dup")).thenReturn(true);

        service.apply(envelope("dup", new TtsCallbackPayload(
            50L, 100L, "SUCCEEDED", "interview/tts/50/100.mp3", 2.4, null
        )));

        verify(messageRepository, never()).findById(any());
    }

    @Test
    void payload_acceptsAudioUrlAliasAndLowercaseStatus() throws Exception {
        TtsCallbackPayload payload = JSON.readValue("""
            {
              "sessionId": 50,
              "messageId": 100,
              "status": "succeeded",
              "audioUrl": "interview/tts/50/100.mp3",
              "durationSec": 2.4
            }
            """, TtsCallbackPayload.class);

        assertThat(payload.isSucceeded()).isTrue();
        assertThat(payload.audioKey()).isEqualTo("interview/tts/50/100.mp3");
    }

    private TtsCallbackEnvelope envelope(String messageId, TtsCallbackPayload payload) {
        return new TtsCallbackEnvelope(messageId, "callback.tts", "1", "t",
            null, "ai", payload, null);
    }

    private boolean isNotice(Object event, RealtimeNotifyEvent.Channel channel, Long id) {
        if (!(event instanceof RealtimeNotifyEvent realtimeNotifyEvent)) {
            return false;
        }
        return realtimeNotifyEvent.channel() == channel
            && realtimeNotifyEvent.id().equals(id)
            && realtimeNotifyEvent.type() == SseEventType.SESSION_MESSAGE;
    }

    private Object argThatObject(Predicate<Object> predicate) {
        return argThat(predicate::test);
    }

    private InterviewSession sessionFixture(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}
