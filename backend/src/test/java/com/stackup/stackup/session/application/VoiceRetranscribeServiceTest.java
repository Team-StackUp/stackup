package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
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
class VoiceRetranscribeServiceTest {

    private static final String KEY = "interview/voice/raw/10/200.webm";

    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks VoiceRetranscribeService service;

    @Test
    void retranscribe_resetsToTranscribingAndRepublishes() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage failed = failedVoiceAnswer(session, KEY);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L))
            .thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(failed));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.of(failed));

        MessageResult result = service.retranscribe(1L, 10L, 200L);

        assertThat(failed.getStatus()).isEqualTo(MessageStatus.CREATED);
        assertThat(failed.getContent())
            .isEqualTo(InterviewMessage.VOICE_TRANSCRIPTION_PENDING_TEXT);
        assertThat(result.content()).isEqualTo(InterviewMessage.VOICE_TRANSCRIPTION_PENDING_TEXT);
        // 오디오는 지우지 않는다 — 같은 파일로 다시 전사하는 것이 이 기능의 전부다.
        assertThat(failed.getAudioFilePath()).isEqualTo(KEY);

        ArgumentCaptor<VoiceAnswerUploadedEvent> captor =
            ArgumentCaptor.forClass(VoiceAnswerUploadedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().messageId()).isEqualTo(200L);
        assertThat(captor.getValue().audioS3Key()).isEqualTo(KEY);
        assertThat(captor.getValue().contentType()).isEqualTo("audio/webm");
    }

    @Test
    void retranscribe_derivesContentTypeFromKeyExtension() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage failed = failedVoiceAnswer(session, "interview/voice/raw/10/200.wav");

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L))
            .thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(failed));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.of(failed));

        service.retranscribe(1L, 10L, 200L);

        ArgumentCaptor<VoiceAnswerUploadedEvent> captor =
            ArgumentCaptor.forClass(VoiceAnswerUploadedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().contentType()).isEqualTo("audio/wav");
    }

    @Test
    void retranscribe_rejectsWhenAlreadyAnsweredAgain() {
        // 실패 뒤 다시 답변했다면 그 답변이 이 턴의 진짜 답이다. 여기서 전사를 되살리면
        // 뒤늦은 콜백이 지나간 턴을 덮어쓴다.
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage failed = failedVoiceAnswer(session, KEY);
        InterviewMessage retyped = InterviewMessage.interviewee(session, 5, "다시 답변", null, null);
        ReflectionTestUtils.setField(retyped, "id", 201L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L))
            .thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(failed));
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.of(retyped));

        assertThatThrownBy(() -> service.retranscribe(1L, 10L, 200L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.VOICE_RETRANSCRIBE_NOT_ALLOWED);
        verify(events, never()).publishEvent(any(VoiceAnswerUploadedEvent.class));
    }

    @Test
    void retranscribe_rejectsMessageThatDidNotFail() {
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage ok = InterviewMessage.voiceInterviewee(session, 4, null, null);
        ReflectionTestUtils.setField(ok, "id", 200L);
        ok.attachAudio(KEY);
        ok.completeWithTranscript("정상 전사된 답변");

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L))
            .thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(ok));

        assertThatThrownBy(() -> service.retranscribe(1L, 10L, 200L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.VOICE_RETRANSCRIBE_NOT_ALLOWED);
        verify(events, never()).publishEvent(any(VoiceAnswerUploadedEvent.class));
    }

    @Test
    void retranscribe_rejectsMessageWithoutAudio() {
        // 업로드 자체가 실패해 오디오가 없으면 되살릴 원본이 없다.
        InterviewSession session = sessionInProgress(10L);
        InterviewMessage failed = InterviewMessage.voiceInterviewee(session, 4, null, null);
        ReflectionTestUtils.setField(failed, "id", 200L);
        failed.failVoiceTranscription();

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L))
            .thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.retranscribe(1L, 10L, 200L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.VOICE_RETRANSCRIBE_NOT_ALLOWED);
    }

    @Test
    void retranscribe_rejectsMessageFromAnotherSession() {
        InterviewSession session = sessionInProgress(10L);
        InterviewSession other = sessionInProgress(11L);
        InterviewMessage failed = failedVoiceAnswer(other, KEY);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L))
            .thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.retranscribe(1L, 10L, 200L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.VOICE_MESSAGE_NOT_FOUND);
    }

    @Test
    void retranscribe_rejectsFinishedSession() {
        InterviewSession session = sessionFixture(10L);  // CREATED — IN_PROGRESS 아님

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L))
            .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.retranscribe(1L, 10L, 200L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.SESSION_INVALID_STATE);
    }

    @Test
    void retranscribe_rejectsSessionNotOwnedByUser() {
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retranscribe(1L, 10L, 200L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.SESSION_NOT_FOUND);
    }

    private InterviewMessage failedVoiceAnswer(InterviewSession session, String key) {
        InterviewMessage question = InterviewMessage.interviewer(session, 3, "ACID 설명해주세요");
        ReflectionTestUtils.setField(question, "id", 100L);
        InterviewMessage m = InterviewMessage.voiceInterviewee(session, 4, question, null);
        ReflectionTestUtils.setField(m, "id", 200L);
        m.attachAudio(key);
        m.failVoiceTranscription();
        return m;
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
