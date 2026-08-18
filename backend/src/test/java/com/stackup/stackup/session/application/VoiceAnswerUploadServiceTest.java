package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.session.application.VoiceAnswerUploadService.VoicePlaceholder;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.event.VoiceAnswerUploadedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.MessageStatus;
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
class VoiceAnswerUploadServiceTest {

    private static final String KEY = "interview/voice/raw/10/200.webm";

    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks VoiceAnswerUploadService service;

    // ── createVoicePlaceholder ────────────────────────────────────────────────

    @Test
    void createVoicePlaceholder_insertsAfterLatestQuestion() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 3, "Tell me about ACID.");
        ReflectionTestUtils.setField(question, "id", 100L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.of(question));
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 200L);
            return m;
        });

        VoicePlaceholder vp = service.createVoicePlaceholder(1L, 10L, "idem-1");

        assertThat(vp.placeholder().getId()).isEqualTo(200L);
        assertThat(vp.placeholder().getSequenceNumber()).isEqualTo(4);
        assertThat(vp.placeholder().getStatus()).isEqualTo(MessageStatus.CREATED);
        assertThat(vp.placeholder().getAudioFilePath()).isNull();
        assertThat(vp.parentQuestion()).isSameAs(question);
    }

    @Test
    void createVoicePlaceholder_returnsExistingOnIdempotencyHit() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "q");
        InterviewMessage existing = InterviewMessage.voiceInterviewee(session, 2, question, "idem-1");
        ReflectionTestUtils.setField(existing, "id", 200L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySession_IdAndIdempotencyKey(10L, "idem-1"))
            .thenReturn(Optional.of(existing));

        VoicePlaceholder vp = service.createVoicePlaceholder(1L, 10L, "idem-1");

        assertThat(vp.placeholder()).isSameAs(existing);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void createVoicePlaceholder_rejectsWhenSessionNotFound() {
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createVoicePlaceholder(1L, 10L, null))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_NOT_FOUND));
    }

    @Test
    void createVoicePlaceholder_rejectsWhenSessionIsNotInProgress() {
        InterviewSession session = sessionFixture(10L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.createVoicePlaceholder(1L, 10L, null))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_INVALID_STATE));

        verify(messageRepository, never()).save(any());
    }

    @Test
    void createVoicePlaceholder_rejectsWhenNoQuestionMessageExists() {
        InterviewSession session = sessionInProgress(10L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createVoicePlaceholder(1L, 10L, null))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_INVALID_STATE));
    }

    @Test
    void createVoicePlaceholder_rejectsWhenLastMessageIsNotQuestion() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage priorAnswer = InterviewMessage.interviewee(session, 1, "already answered", null, null);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.of(priorAnswer));

        assertThatThrownBy(() -> service.createVoicePlaceholder(1L, 10L, null))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_INVALID_STATE));

        verify(messageRepository, never()).save(any());
    }

    // ── attachAudioAndRequestAnalysis ─────────────────────────────────────────

    // analyze.voice 는 여기서 직접 발행하지 않는다 — 이벤트만 내고 AFTER_COMMIT 리스너가 발행한다.
    @Test
    void attachAudio_setsPathAndPublishesUploadedEvent() {
        InterviewMessage placeholder = voicePlaceholder(200L);

        when(messageRepository.findById(200L)).thenReturn(Optional.of(placeholder));

        MessageResult result = service.attachAudioAndRequestAnalysis(
            1L, 10L, 200L, KEY, "audio/webm");

        assertThat(result.id()).isEqualTo(200L);
        assertThat(result.audioFilePath()).isEqualTo(KEY);
        assertThat(placeholder.getAudioFilePath()).isEqualTo(KEY);

        ArgumentCaptor<VoiceAnswerUploadedEvent> captor =
            ArgumentCaptor.forClass(VoiceAnswerUploadedEvent.class);
        verify(events).publishEvent(captor.capture());
        VoiceAnswerUploadedEvent event = captor.getValue();
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.sessionId()).isEqualTo(10L);
        assertThat(event.messageId()).isEqualTo(200L);
        assertThat(event.audioS3Key()).isEqualTo(KEY);
        assertThat(event.contentType()).isEqualTo("audio/webm");
    }

    @Test
    void attachAudio_doesNotRepublishWhenAlreadyAttached() {
        InterviewMessage placeholder = voicePlaceholder(200L);
        placeholder.attachAudio(KEY);

        when(messageRepository.findById(200L)).thenReturn(Optional.of(placeholder));

        MessageResult result = service.attachAudioAndRequestAnalysis(
            1L, 10L, 200L, "interview/voice/raw/10/200-retry.webm", "audio/webm");

        assertThat(result.audioFilePath()).isEqualTo(KEY);
        verify(events, never()).publishEvent(any(VoiceAnswerUploadedEvent.class));
    }

    @Test
    void attachAudio_rejectsWhenMessageNotFound() {
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachAudioAndRequestAnalysis(
            1L, 10L, 999L, KEY, "audio/webm"))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.VOICE_MESSAGE_NOT_FOUND));
    }

    // ── failVoiceUpload ───────────────────────────────────────────────────────

    // 업로드 실패 보상: 메시지를 지우지 않고 FAILED 로 두고 SSE 로 알린다(턴 잠김 방지).
    @Test
    void failVoiceUpload_marksFailedAndNotifies() {
        InterviewMessage placeholder = voicePlaceholder(200L);

        when(messageRepository.findById(200L)).thenReturn(Optional.of(placeholder));

        service.failVoiceUpload(10L, 200L);

        assertThat(placeholder.getStatus()).isEqualTo(MessageStatus.FAILED);
        verify(events, org.mockito.Mockito.times(2)).publishEvent(any(RealtimeNotifyEvent.class));
    }

    @Test
    void failVoiceUpload_isNoopWhenMessageMissing() {
        when(messageRepository.findById(200L)).thenReturn(Optional.empty());

        service.failVoiceUpload(10L, 200L);

        verify(events, never()).publishEvent(any());
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private InterviewMessage voicePlaceholder(Long id) {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 1, "Tell me about ACID.");
        ReflectionTestUtils.setField(question, "id", 100L);
        InterviewMessage placeholder = InterviewMessage.voiceInterviewee(session, 2, question, null);
        ReflectionTestUtils.setField(placeholder, "id", id);
        return placeholder;
    }

    private InterviewSession sessionInProgress(Long id) {
        InterviewSession s = sessionFixture(id);
        s.start();
        return s;
    }

    private InterviewSession sessionFixture(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, null, null
        );
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}
