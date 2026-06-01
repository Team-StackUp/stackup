package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.messaging.RealtimeNotifyPublisher;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.FeedbackCallbackEnvelope;
import com.stackup.stackup.session.application.dto.FeedbackCallbackPayload;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionFeedback;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedbackCallbackServiceTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock SessionFeedbackRepository feedbackRepository;
    @Mock ProcessedMessageRepository processedMessageRepository;
    @Mock RealtimeNotifyPublisher realtimeNotifyPublisher;
    @InjectMocks FeedbackCallbackService service;

    @Test
    void apply_insertsFeedbackAndPushesSse() {
        InterviewSession session = sessionFixture(50L);
        FeedbackCallbackEnvelope env = envelope(50L, "fb-1",
            new FeedbackCallbackPayload(50L, 85.0, 80.0, 90.0, 75.0,
                "strength summary", "weakness summary", List.of("Spring", "JPA"), null));

        when(processedMessageRepository.existsById("fb-1")).thenReturn(false);
        when(feedbackRepository.existsBySession_Id(50L)).thenReturn(false);
        when(sessionRepository.findById(50L)).thenReturn(Optional.of(session));
        when(feedbackRepository.save(any(SessionFeedback.class))).thenAnswer(inv -> {
            SessionFeedback f = inv.getArgument(0);
            ReflectionTestUtils.setField(f, "id", 700L);
            return f;
        });

        service.apply(env);

        ArgumentCaptor<SessionFeedback> cap = ArgumentCaptor.forClass(SessionFeedback.class);
        verify(feedbackRepository).save(cap.capture());
        assertThat(cap.getValue().getOverallScore()).isEqualTo(85.0);
        assertThat(cap.getValue().getImprovementKeywords()).contains("Spring");
        verify(realtimeNotifyPublisher).publishToSession(eq(50L), eq(SseEventType.FEEDBACK_READY), any());
    }

    @Test
    void apply_skipsWhenDuplicateMessage() {
        FeedbackCallbackEnvelope env = envelope(50L, "dup",
            new FeedbackCallbackPayload(50L, 80.0, null, null, null, null, null, List.of(), null));
        when(processedMessageRepository.existsById("dup")).thenReturn(true);

        service.apply(env);

        verify(feedbackRepository, never()).save(any(SessionFeedback.class));
    }

    @Test
    void apply_skipsWhenFeedbackAlreadyExists() {
        InterviewSession session = sessionFixture(50L);
        FeedbackCallbackEnvelope env = envelope(50L, "fb-2",
            new FeedbackCallbackPayload(50L, 80.0, null, null, null, null, null, List.of(), null));
        when(processedMessageRepository.existsById("fb-2")).thenReturn(false);
        when(sessionRepository.findById(50L)).thenReturn(Optional.of(session));
        when(feedbackRepository.existsBySession_Id(50L)).thenReturn(true);

        service.apply(env);

        verify(feedbackRepository, never()).save(any(SessionFeedback.class));
    }

    private FeedbackCallbackEnvelope envelope(Long sessionId, String messageId, FeedbackCallbackPayload payload) {
        return new FeedbackCallbackEnvelope(messageId, "callback.feedback", "1", "t", null, "ai", payload, null);
    }

    private InterviewSession sessionFixture(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}
