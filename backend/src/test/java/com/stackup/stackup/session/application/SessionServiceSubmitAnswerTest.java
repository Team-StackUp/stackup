package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.MessageSubmitCommand;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.application.exception.SessionForbiddenException;
import com.stackup.stackup.session.application.exception.SessionInvalidStateException;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceSubmitAnswerTest {

    @Mock InterviewSessionRepository sessionRepo;
    @Mock SessionContextRepository contextRepo;
    @Mock InterviewMessageRepository messageRepo;
    @Mock AnalyzedDocumentRepository documentRepo;
    @Mock UserRepository userRepo;
    @Mock ProcessedMessageRepository processedRepo;
    @Mock SseEventPublisher sse;
    @Mock RabbitMessagePublisher publisher;
    @Mock RabbitMqProperties properties;
    @Mock ApplicationEventPublisher events;

    @InjectMocks SessionService service;

    @Test
    void submitAnswer_inserts_interviewee_message_and_publishes_event() {
        InterviewSession s = inProgress(99L, 1L);
        InterviewMessage lastQ = interviewerMessage(s, 1, 501L);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        when(messageRepo.findMaxSequenceBySessionId(99L)).thenReturn(1);
        when(messageRepo.findFirstBySession_IdOrderBySequenceNumberDesc(99L)).thenReturn(Optional.of(lastQ));
        when(messageRepo.save(any())).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 502L);
            return m;
        });
        when(processedRepo.existsById("session.answer:99:idem-uuid")).thenReturn(false);

        MessageResult r = service.submitAnswer(new MessageSubmitCommand(
                99L, 1L, "내 답변", "idem-uuid"));

        assertThat(r.id()).isEqualTo(502L);
        assertThat(r.sequenceNumber()).isEqualTo(2);
        assertThat(r.role()).isEqualTo(MessageRole.INTERVIEWEE);
        verify(events).publishEvent(any(AnswerSubmittedEvent.class));
        verify(processedRepo).save(argThat(pm ->
                pm.getMessageId().equals("session.answer:99:idem-uuid")));
    }

    @Test
    void submitAnswer_returns_cached_message_when_idempotency_key_duplicate() {
        InterviewSession s = inProgress(99L, 1L);
        InterviewMessage cached = intervieweeMessage(s, 2, 502L);
        when(processedRepo.existsById("session.answer:99:idem-uuid")).thenReturn(true);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        when(messageRepo.findFirstBySession_IdOrderBySequenceNumberDesc(99L))
                .thenReturn(Optional.of(cached));

        MessageResult r = service.submitAnswer(new MessageSubmitCommand(
                99L, 1L, "내 답변", "idem-uuid"));

        assertThat(r.id()).isEqualTo(502L);
        verify(messageRepo, never()).save(any());
        verify(events, never()).publishEvent(any(AnswerSubmittedEvent.class));
    }

    @Test
    void submitAnswer_throws_when_session_not_in_progress() {
        InterviewSession s = ready(99L, 1L);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        when(processedRepo.existsById(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.submitAnswer(new MessageSubmitCommand(99L, 1L, "A", "k")))
                .isInstanceOf(SessionInvalidStateException.class);
    }

    @Test
    void submitAnswer_throws_when_user_mismatch() {
        InterviewSession s = inProgress(99L, 2L); // 다른 user 소유
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        // 소유자 불일치 체크는 멱등 체크보다 먼저 실행되므로 processedRepo stub 불필요

        assertThatThrownBy(() -> service.submitAnswer(new MessageSubmitCommand(99L, 1L, "A", "k")))
                .isInstanceOf(SessionForbiddenException.class);
    }

    @Test
    void submitAnswer_with_same_key_in_different_sessions_does_not_collide() {
        when(processedRepo.existsById("session.answer:88:shared-key")).thenReturn(false);
        InterviewSession s88 = inProgress(88L, 1L);
        InterviewMessage parent88 = interviewerMessage(s88, 1, 401L);
        when(sessionRepo.findByIdAndIsDeletedFalse(88L)).thenReturn(Optional.of(s88));
        when(messageRepo.findMaxSequenceBySessionId(88L)).thenReturn(1);
        when(messageRepo.findFirstBySession_IdOrderBySequenceNumberDesc(88L)).thenReturn(Optional.of(parent88));
        when(messageRepo.save(any())).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 402L);
            return m;
        });

        MessageResult r = service.submitAnswer(new MessageSubmitCommand(88L, 1L, "다른 세션 답변", "shared-key"));

        assertThat(r.id()).isEqualTo(402L);
        verify(processedRepo).save(argThat(pm ->
            pm.getMessageId().equals("session.answer:88:shared-key")));
    }

    @Test
    void submitAnswer_throws_when_last_message_is_not_interviewer() {
        InterviewSession s = inProgress(99L, 1L);
        InterviewMessage prevAnswer = intervieweeMessage(s, 2, 502L); // 직전이 답변
        when(processedRepo.existsById(anyString())).thenReturn(false);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        when(messageRepo.findFirstBySession_IdOrderBySequenceNumberDesc(99L)).thenReturn(Optional.of(prevAnswer));

        assertThatThrownBy(() -> service.submitAnswer(new MessageSubmitCommand(99L, 1L, "A", "k")))
                .isInstanceOf(SessionInvalidStateException.class)
                .hasMessageContaining("질문");
    }

    private User userStub(Long id) {
        User user = User.createGithubUser(
                123L, "stub-user", "stub@example.com", null, "encrypted-token");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InterviewSession sessionStub(Long sessionId, Long userId, SessionStatus status) {
        User user = userStub(userId);
        InterviewSession s = InterviewSession.create(
                user, "Test Session", null,
                SessionMode.ONLINE, InterviewType.TECHNICAL, JobCategory.BACKEND,
                10, 60);
        ReflectionTestUtils.setField(s, "id", sessionId);
        ReflectionTestUtils.setField(s, "status", status);
        return s;
    }

    private InterviewSession inProgress(Long sessionId, Long userId) {
        return sessionStub(sessionId, userId, SessionStatus.IN_PROGRESS);
    }

    private InterviewSession ready(Long sessionId, Long userId) {
        return sessionStub(sessionId, userId, SessionStatus.READY);
    }

    private InterviewMessage interviewerMessage(InterviewSession session, int seq, Long id) {
        InterviewMessage m = InterviewMessage.interviewer(session, seq, "면접 질문입니다.", null);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private InterviewMessage intervieweeMessage(InterviewSession session, int seq, Long id) {
        InterviewMessage m = InterviewMessage.interviewee(session, seq, "답변입니다.", null);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}
