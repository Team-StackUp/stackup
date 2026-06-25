package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.session.application.event.SessionEndedEvent;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionTimeoutServiceTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks SessionTimeoutService service;

    @Test
    void endTimedOut_withAnswer_completesAndEmitsEndedEvent() {
        InterviewSession session = inProgressFixture(50L);
        when(sessionRepository.findById(50L)).thenReturn(Optional.of(session));
        when(messageRepository.existsBySession_IdAndRole(50L, MessageRole.INTERVIEWEE)).thenReturn(true);

        service.endTimedOut(50L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        verify(events).publishEvent(any(SessionEndedEvent.class));
    }

    @Test
    void endTimedOut_withoutAnswer_interruptsAndDoesNotTriggerFeedback() {
        InterviewSession session = inProgressFixture(51L);
        when(sessionRepository.findById(51L)).thenReturn(Optional.of(session));
        when(messageRepository.existsBySession_IdAndRole(51L, MessageRole.INTERVIEWEE)).thenReturn(false);

        service.endTimedOut(51L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        // 답변이 없으면 피드백을 만들지 않는다 — SessionEndedEvent 미발행.
        verify(events, never()).publishEvent(any(SessionEndedEvent.class));
    }

    @Test
    void endTimedOut_notInProgress_isNoop() {
        InterviewSession session = inProgressFixture(52L);
        session.end();  // 이미 COMPLETED
        when(sessionRepository.findById(52L)).thenReturn(Optional.of(session));

        service.endTimedOut(52L);

        verify(events, never()).publishEvent(any());
        verify(messageRepository, never()).existsBySession_IdAndRole(any(), any());
    }

    private InterviewSession inProgressFixture(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, null, null);
        ReflectionTestUtils.setField(s, "id", id);
        s.start();
        return s;
    }
}
