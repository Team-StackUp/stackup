package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.QuestionsCallbackEnvelope;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload.GeneratedQuestion;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuestionsCallbackServiceTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock ProcessedMessageRepository processedMessageRepository;
    @Mock SseEventPublisher sseEventPublisher;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @InjectMocks QuestionsCallbackService service;

    @Test
    void apply_poolInsertsFirstQuestionAndPushesSse() {
        InterviewSession session = sessionFixture(11L, SessionStatus.READY);
        QuestionsCallbackEnvelope env = poolEnvelope(11L, List.of(
            new GeneratedQuestion("INTRO", "자기소개"),
            new GeneratedQuestion("TECH", "JPA?")
        ));
        when(processedMessageRepository.existsById("m-1")).thenReturn(false);
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 500L);
            return m;
        });

        service.apply(env);

        verify(messageRepository).save(any(InterviewMessage.class));
        verify(sseEventPublisher).publishToSession(eq(11L), eq(SseEventType.SESSION_MESSAGE), any());
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
        assertThat(session.getTotalQuestionCount()).isEqualTo(1);
    }

    @Test
    void apply_followupAutoEndsSessionAtMaxQuestions() {
        InterviewSession session = sessionFixture(11L, SessionStatus.IN_PROGRESS);
        // maxQuestions=5 ; total=4 → followup INSERT 시 5 도달 → 자동 종료
        ReflectionTestUtils.setField(session, "totalQuestionCount", 4);

        QuestionsCallbackEnvelope env = followupEnvelope(11L, 200L, "꼬리?");
        when(processedMessageRepository.existsById("m-2")).thenReturn(false);
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(parentMessageFixture(session)));
        when(messageRepository.countBySession_Id(11L)).thenReturn(8L);
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 700L);
            return m;
        });

        service.apply(env);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getTotalQuestionCount()).isEqualTo(5);
    }

    @Test
    void apply_skipsDuplicateMessageId() {
        QuestionsCallbackEnvelope env = poolEnvelope(11L, List.of(new GeneratedQuestion("X", "Q")));
        when(processedMessageRepository.existsById("m-1")).thenReturn(true);

        service.apply(env);

        verify(sessionRepository, never()).findById(any());
        verify(messageRepository, never()).save(any(InterviewMessage.class));
    }

    private QuestionsCallbackEnvelope poolEnvelope(Long sessionId, List<GeneratedQuestion> questions) {
        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            sessionId, "POOL", questions, null, null, null
        );
        return new QuestionsCallbackEnvelope("m-1", "callback.questions", "1", "t", null, "ai", payload, null);
    }

    private QuestionsCallbackEnvelope followupEnvelope(Long sessionId, Long parentId, String followup) {
        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            sessionId, "FOLLOWUP", null, parentId, followup, null
        );
        return new QuestionsCallbackEnvelope("m-2", "callback.questions", "1", "t", null, "ai", payload, null);
    }

    private InterviewSession sessionFixture(Long id, SessionStatus status) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.ONLINE,
            InterviewType.TECHNICAL, JobCategory.BACKEND, 5, 30
        );
        ReflectionTestUtils.setField(s, "id", id);
        if (status == SessionStatus.IN_PROGRESS || status == SessionStatus.COMPLETED) {
            s.start();
        }
        return s;
    }

    private InterviewMessage parentMessageFixture(InterviewSession session) {
        InterviewMessage m = InterviewMessage.interviewer(session, 1, "Q?");
        ReflectionTestUtils.setField(m, "id", 200L);
        return m;
    }
}
