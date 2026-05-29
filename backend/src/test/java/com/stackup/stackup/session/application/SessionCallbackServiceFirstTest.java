package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.QuestionsCallbackEnvelope;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionCallbackServiceFirstTest {

    @Mock InterviewSessionRepository sessionRepo;
    @Mock InterviewMessageRepository messageRepo;
    @Mock ProcessedMessageRepository processedRepo;
    @Mock SseEventPublisher sse;

    SessionCallbackService service;

    @BeforeEach
    void setUp() {
        service = new SessionCallbackService(sessionRepo, messageRepo, processedRepo, sse);
    }

    @Test
    void first_marks_in_progress_inserts_first_message_publishes_sse() {
        InterviewSession s = ready(99L);
        QuestionsCallbackPayload p = new QuestionsCallbackPayload(
            99L, "FIRST", "PROJECT_DEEP_DIVE", "Q1", null, null, null);
        QuestionsCallbackEnvelope env = envelope("mid-1", p);

        when(processedRepo.existsById("mid-1")).thenReturn(false);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        when(messageRepo.findMaxSequenceBySessionId(99L)).thenReturn(0);
        when(messageRepo.save(any())).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 501L);
            return m;
        });

        service.apply(env);

        assertThat(s.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(s.getStartedAt()).isNotNull();
        assertThat(s.getTotalQuestionCount()).isEqualTo(1);

        ArgumentCaptor<InterviewMessage> mc = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(messageRepo).save(mc.capture());
        assertThat(mc.getValue().getSequenceNumber()).isEqualTo(1);
        assertThat(mc.getValue().getContent()).isEqualTo("Q1");
        assertThat(mc.getValue().getRole()).isEqualTo(MessageRole.INTERVIEWER);

        verify(sse).publishToSession(eq(99L), eq(SseEventType.SESSION_MESSAGE), any());
        verify(processedRepo).save(any(ProcessedMessage.class));
    }

    @Test
    void duplicate_messageId_is_skipped() {
        when(processedRepo.existsById("mid-1")).thenReturn(true);
        service.apply(envelope("mid-1", firstPayload()));
        verifyNoInteractions(sessionRepo);
    }

    @Test
    void first_with_blank_question_publishes_error_keeps_ready() {
        InterviewSession s = ready(99L);
        when(processedRepo.existsById(any())).thenReturn(false);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));

        QuestionsCallbackPayload p = new QuestionsCallbackPayload(
            99L, "FIRST", null, null, null, null, null);
        service.apply(envelope("mid-empty", p));

        verify(sse).publishToSession(eq(99L), eq(SseEventType.ERROR), any());
        verifyNoInteractions(messageRepo);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.READY);
    }

    @Test
    void first_dropped_when_session_already_in_progress() {
        InterviewSession s = inProgressSession(99L); // already IN_PROGRESS
        when(processedRepo.existsById("mid-redeliver")).thenReturn(false);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));

        QuestionsCallbackPayload p = new QuestionsCallbackPayload(
            99L, "FIRST", "C", "Q", null, null, null);
        service.apply(envelope("mid-redeliver", p));

        verify(messageRepo, never()).save(any(InterviewMessage.class));
        verify(processedRepo).save(any()); // 멱등 기록은 됨 — 재배달 시 skip
        assertThat(s.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS); // unchanged
    }

    private InterviewSession ready(Long id) {
        User user = User.createGithubUser(1L, "u", "u@e.com", "url", "tok");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.ONLINE, InterviewType.TECHNICAL,
            JobCategory.BACKEND, 10, 60);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private InterviewSession inProgressSession(Long id) {
        InterviewSession s = ready(id);
        s.markInProgress();
        return s;
    }

    private QuestionsCallbackPayload firstPayload() {
        return new QuestionsCallbackPayload(99L, "FIRST", "C", "Q", null, null, null);
    }

    private QuestionsCallbackEnvelope envelope(String mid, QuestionsCallbackPayload p) {
        return new QuestionsCallbackEnvelope(mid, "callback.questions", "v1",
            "trace-1", Instant.now(), "ai-server", p,
            new MessageContext(1L, 99L, null, null));
    }
}
