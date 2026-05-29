package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.exception.SessionForbiddenException;
import com.stackup.stackup.session.application.exception.SessionInvalidStateException;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.JobCategory;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceEndTest {

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
    void end_in_progress_completes_session_and_pushes_state() {
        InterviewSession s = inProgress(99L, 1L);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));

        SessionResult r = service.end(99L, 1L, false);

        assertThat(r.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(s.getEndedAt()).isNotNull();
        verify(sse).publishToSession(eq(99L), eq(SseEventType.SESSION_STATE), any());
    }

    @Test
    void end_with_cancel_true_sets_cancelled_status() {
        InterviewSession s = inProgress(99L, 1L);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));

        SessionResult r = service.end(99L, 1L, true);

        assertThat(r.status()).isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    void end_other_users_session_throws_forbidden() {
        InterviewSession s = inProgress(99L, 2L);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.end(99L, 1L, false))
                .isInstanceOf(SessionForbiddenException.class);
    }

    @Test
    void end_throws_invalid_state_when_already_completed() {
        InterviewSession s = sessionStub(99L, 1L, SessionStatus.COMPLETED);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.end(99L, 1L, false))
                .isInstanceOf(SessionInvalidStateException.class);
    }

    @Test
    void end_throws_invalid_state_when_already_cancelled() {
        InterviewSession s = sessionStub(99L, 1L, SessionStatus.CANCELLED);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.end(99L, 1L, false))
                .isInstanceOf(SessionInvalidStateException.class);
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
}
