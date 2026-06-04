package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.domain.User;
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
class InterviewMessageServiceTest {

    private static final Long USER_ID    = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long MESSAGE_ID = 100L;

    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock ObjectStorageClient storage;
    @Mock ApplicationEventPublisher events;
    @InjectMocks InterviewMessageService service;

    @Test
    void submitAnswer_insertsIntervieweeAndPublishesEvent() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "Q1?");
        ReflectionTestUtils.setField(question, "id", 100L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L)).thenReturn(Optional.of(question));
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 200L);
            return m;
        });

        MessageResult result = service.submitAnswer(1L, 10L, "answer text", null);

        assertThat(result.id()).isEqualTo(200L);
        assertThat(result.sequenceNumber()).isEqualTo(2);
        verify(events).publishEvent(any(AnswerSubmittedEvent.class));
    }

    @Test
    void submitAnswer_returnsExistingWhenIdempotencyKeyHit() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage prior = InterviewMessage.interviewee(session, 2, "prior", null, "key-abc");
        ReflectionTestUtils.setField(prior, "id", 200L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySession_IdAndIdempotencyKey(10L, "key-abc"))
            .thenReturn(Optional.of(prior));

        MessageResult result = service.submitAnswer(1L, 10L, "retry", "key-abc");

        assertThat(result.id()).isEqualTo(200L);
        verify(messageRepository, never()).save(any(InterviewMessage.class));
        verify(events, never()).publishEvent(any(AnswerSubmittedEvent.class));
    }

    @Test
    void submitAnswer_rejectsWhenPreviousMessageNotInterviewer() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage prior = InterviewMessage.interviewee(session, 1, "prev answer", null, null);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L)).thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> service.submitAnswer(1L, 10L, "x", null))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void submitAnswer_allowsTextRetryAfterFailedVoiceAnswer() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "Q1?");
        ReflectionTestUtils.setField(question, "id", 100L);
        InterviewMessage failedVoice = InterviewMessage.voiceInterviewee(session, 2, question, null);
        ReflectionTestUtils.setField(failedVoice, "id", 200L);
        failedVoice.attachAudio("interview/voice/raw/10/200.webm");
        failedVoice.failVoiceTranscription();

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L)).thenReturn(Optional.of(failedVoice));
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 201L);
            return m;
        });

        MessageResult result = service.submitAnswer(1L, 10L, "retry as text", null);

        assertThat(result.id()).isEqualTo(201L);
        assertThat(result.sequenceNumber()).isEqualTo(3);
        assertThat(result.parentMessageId()).isEqualTo(100L);
        ArgumentCaptor<AnswerSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(AnswerSubmittedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().parentQuestionMessageId()).isEqualTo(100L);
        assertThat(eventCaptor.getValue().answerMessageId()).isEqualTo(201L);
    }

    // ── 라이브 TTS 세그먼트 프록시 테스트 ─────────────────────────────────────────

    @Test
    void streamAudioSegment_rejects_unknown_extension() {
        // ownedSession 통과하도록 sessionRepository.findByIdAndUser_IdAndDeletedFalse 스텁
        given(sessionRepository.findByIdAndUser_IdAndDeletedFalse(SESSION_ID, USER_ID))
            .willReturn(Optional.of(sessionInProgress(SESSION_ID)));
        assertThatThrownBy(() -> service.streamAudioSegment(USER_ID, SESSION_ID, MESSAGE_ID, 0, "exe"))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void streamAudioSegment_builds_convention_key_and_streams() {
        InterviewSession session = sessionInProgress(SESSION_ID);
        InterviewMessage message = InterviewMessage.interviewer(session, 1, "Q?");
        ReflectionTestUtils.setField(message, "id", MESSAGE_ID);

        given(sessionRepository.findByIdAndUser_IdAndDeletedFalse(SESSION_ID, USER_ID))
            .willReturn(Optional.of(session));
        given(messageRepository.findById(MESSAGE_ID)).willReturn(Optional.of(message));
        given(storage.get(anyString())).willReturn(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}));

        var audio = service.streamAudioSegment(USER_ID, SESSION_ID, MESSAGE_ID, 2, "mp3");

        verify(storage).get("interview/tts/%d/%d/seg-2.mp3".formatted(SESSION_ID, MESSAGE_ID));
        assertThat(audio.contentType()).isEqualTo("audio/mpeg");
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private InterviewSession sessionInProgress(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, null, null
        );
        ReflectionTestUtils.setField(s, "id", id);
        s.start();
        return s;
    }
}
