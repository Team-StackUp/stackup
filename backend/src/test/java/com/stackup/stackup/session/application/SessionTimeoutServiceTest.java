package com.stackup.stackup.session.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.time.Instant;
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
    void endTimedOut_withAnswer_claimsCompletedAndEmitsEndedEvent() {
        InterviewSession session = inProgressFixture(50L);
        when(sessionRepository.findById(50L)).thenReturn(Optional.of(session));
        when(messageRepository.existsBySession_IdAndRole(50L, MessageRole.INTERVIEWEE)).thenReturn(true);
        when(sessionRepository.finishIfInProgress(eq(50L), eq(SessionStatus.COMPLETED), any(Instant.class)))
            .thenReturn(1);

        service.endTimedOut(50L);

        verify(events).publishEvent(any(SessionEndedEvent.class));
    }

    @Test
    void endTimedOut_withoutAnswer_claimsInterruptedAndDoesNotTriggerFeedback() {
        InterviewSession session = inProgressFixture(51L);
        when(sessionRepository.findById(51L)).thenReturn(Optional.of(session));
        when(messageRepository.existsBySession_IdAndRole(51L, MessageRole.INTERVIEWEE)).thenReturn(false);
        when(sessionRepository.finishIfInProgress(eq(51L), eq(SessionStatus.INTERRUPTED), any(Instant.class)))
            .thenReturn(1);

        service.endTimedOut(51L);

        // 답변이 없으면 피드백을 만들지 않는다 — SessionEndedEvent 미발행.
        verify(events, never()).publishEvent(any(SessionEndedEvent.class));
    }

    @Test
    void endTimedOut_lostRace_emitsNothing() {
        // 조건부 UPDATE 가 0행 → 다른 트랜잭션이 먼저 종료. 어떤 이벤트도 발행하지 않는다(중복 방지).
        InterviewSession session = inProgressFixture(53L);
        when(sessionRepository.findById(53L)).thenReturn(Optional.of(session));
        when(messageRepository.existsBySession_IdAndRole(53L, MessageRole.INTERVIEWEE)).thenReturn(true);
        when(sessionRepository.finishIfInProgress(eq(53L), eq(SessionStatus.COMPLETED), any(Instant.class)))
            .thenReturn(0);

        service.endTimedOut(53L);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void endTimedOut_notInProgress_isNoop() {
        InterviewSession session = inProgressFixture(52L);
        session.end();  // 이미 COMPLETED
        when(sessionRepository.findById(52L)).thenReturn(Optional.of(session));

        service.endTimedOut(52L);

        verify(events, never()).publishEvent(any());
        verify(messageRepository, never()).existsBySession_IdAndRole(any(), any());
        verify(sessionRepository, never()).finishIfInProgress(any(), any(), any());
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
