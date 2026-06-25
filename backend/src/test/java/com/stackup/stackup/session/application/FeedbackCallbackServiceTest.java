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
import com.stackup.stackup.session.application.dto.AnswerCoachingItem;
import com.stackup.stackup.session.application.dto.FeedbackCallbackEnvelope;
import com.stackup.stackup.session.application.dto.FeedbackCallbackPayload;
import com.stackup.stackup.session.application.dto.PanelBreakdownItem;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedbackCallbackServiceTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock SessionFeedbackRepository feedbackRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock ProcessedMessageRepository processedMessageRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks FeedbackCallbackService service;

    @Test
    void apply_insertsFeedbackAndPushesSse() {
        InterviewSession session = sessionFixture(50L);
        FeedbackCallbackEnvelope env = envelope(50L, "fb-1",
            new FeedbackCallbackPayload(50L, 85.0, 80.0, 90.0, 75.0,
                "strength summary", "weakness summary", List.of("Spring", "JPA"),
                List.of("Redis 분산 락 직접 구현"),
                List.of(new PanelBreakdownItem("기술", "기술 정확도·깊이", 80.0,
                    "설계 깊이", "테스트 부족", "상세 평가 문단", "근거")),
                List.of(), null));

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
        assertThat(cap.getValue().getPanelBreakdown()).contains("기술");
        assertThat(cap.getValue().getStudyPlan()).contains("Redis");

        ArgumentCaptor<Object> evCap = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).publishEvent(evCap.capture());
        assertThat(evCap.getAllValues()).anySatisfy(e -> {
            assertThat(e).isInstanceOf(RealtimeNotifyEvent.class);
            RealtimeNotifyEvent rne = (RealtimeNotifyEvent) e;
            assertThat(rne.channel()).isEqualTo(RealtimeNotifyEvent.Channel.SESSION);
            assertThat(rne.type()).isEqualTo(SseEventType.FEEDBACK_READY);
        });
    }

    @Test
    void apply_writesAnswerCoachingToMessages() {
        InterviewSession session = sessionFixture(50L);
        InterviewMessage answer = InterviewMessage.interviewee(session, 2, "내 답변", null, null);
        ReflectionTestUtils.setField(answer, "id", 600L);

        FeedbackCallbackEnvelope env = envelope(50L, "fb-coach",
            new FeedbackCallbackPayload(50L, 80.0, null, null, null, null, null,
                List.of(), List.of(), List.of(),
                List.of(new AnswerCoachingItem(600L, "모범 답안", "리라이트", "두괄식으로")),
                null));

        when(processedMessageRepository.existsById("fb-coach")).thenReturn(false);
        when(feedbackRepository.existsBySession_Id(50L)).thenReturn(false);
        when(sessionRepository.findById(50L)).thenReturn(Optional.of(session));
        when(feedbackRepository.save(any(SessionFeedback.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findById(600L)).thenReturn(Optional.of(answer));

        service.apply(env);

        assertThat(answer.getModelAnswer()).isEqualTo("모범 답안");
        assertThat(answer.getAnswerRewrite()).isEqualTo("리라이트");
        assertThat(answer.getCoachingComment()).isEqualTo("두괄식으로");
        verify(messageRepository).save(answer);
    }

    @Test
    void apply_skipsWhenDuplicateMessage() {
        FeedbackCallbackEnvelope env = envelope(50L, "dup",
            new FeedbackCallbackPayload(50L, 80.0, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), null));
        when(processedMessageRepository.existsById("dup")).thenReturn(true);

        service.apply(env);

        verify(feedbackRepository, never()).save(any(SessionFeedback.class));
    }

    @Test
    void apply_skipsWhenFeedbackAlreadyExists() {
        InterviewSession session = sessionFixture(50L);
        FeedbackCallbackEnvelope env = envelope(50L, "fb-2",
            new FeedbackCallbackPayload(50L, 80.0, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), null));
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
        InterviewSession s = InterviewSession.create(user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, null, null);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}
