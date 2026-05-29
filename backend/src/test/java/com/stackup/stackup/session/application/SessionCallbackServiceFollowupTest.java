package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.messaging.MessageContext;
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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionCallbackServiceFollowupTest {

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
    void followup_inserts_interviewer_message_links_parent_pushes_sse() {
        InterviewSession s = inProgressWith(99L, 1, 10); // count=1, max=10
        InterviewMessage parentAnswer = intervieweeMessage(s, 2, 502L);
        when(processedRepo.existsById("mid-2")).thenReturn(false);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        when(messageRepo.findById(502L)).thenReturn(Optional.of(parentAnswer));
        when(messageRepo.findMaxSequenceBySessionId(99L)).thenReturn(2);
        when(messageRepo.save(any())).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 503L);
            return m;
        });

        QuestionsCallbackPayload p = new QuestionsCallbackPayload(
            99L, "FOLLOWUP", "CS_FUNDAMENTAL", "왜?", 502L, null, null);
        service.apply(envelope("mid-2", p));

        ArgumentCaptor<InterviewMessage> mc = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(messageRepo).save(mc.capture());
        assertThat(mc.getValue().getRole()).isEqualTo(MessageRole.INTERVIEWER);
        assertThat(mc.getValue().getSequenceNumber()).isEqualTo(3);
        assertThat(mc.getValue().getParentMessage()).isSameAs(parentAnswer);
        assertThat(s.getTotalQuestionCount()).isEqualTo(2);
        verify(sse).publishToSession(eq(99L), eq(SseEventType.SESSION_MESSAGE), any());
    }

    @Test
    void followup_with_unknown_parent_message_logs_and_drops() {
        when(processedRepo.existsById(any())).thenReturn(false);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L))
            .thenReturn(Optional.of(inProgressWith(99L, 1, 10)));
        when(messageRepo.findById(999L)).thenReturn(Optional.empty());

        QuestionsCallbackPayload p = new QuestionsCallbackPayload(
            99L, "FOLLOWUP", "C", "?", 999L, null, null);
        service.apply(envelope("mid-3", p));

        verify(messageRepo, never()).save(any(InterviewMessage.class));
        verify(processedRepo).save(any()); // 처리 완료로 기록 (재시도 무의미)
    }

    @Test
    void followup_auto_completes_session_when_max_reached() {
        InterviewSession s = inProgressWith(99L, 9, 10); // 다음 INSERT 후 10 도달
        InterviewMessage parent = intervieweeMessage(s, 18, 519L);
        when(processedRepo.existsById(any())).thenReturn(false);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        when(messageRepo.findById(519L)).thenReturn(Optional.of(parent));
        when(messageRepo.findMaxSequenceBySessionId(99L)).thenReturn(18);
        when(messageRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestionsCallbackPayload p = new QuestionsCallbackPayload(
            99L, "FOLLOWUP", "C", "마지막 질문?", 519L, null, null);
        service.apply(envelope("mid-final", p));

        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(s.getEndedAt()).isNotNull();
        verify(sse).publishToSession(eq(99L), eq(SseEventType.SESSION_STATE),
            argThat(payload -> payload.toString().contains("COMPLETED")));
    }

    @Test
    void end_kind_completes_session_without_insert() {
        InterviewSession s = inProgressWith(99L, 5, 10); // max 미도달이지만 AI 가 조기 종료 신호
        when(processedRepo.existsById(any())).thenReturn(false);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));

        QuestionsCallbackPayload p = new QuestionsCallbackPayload(
            99L, "END", null, null, null, Map.of("specificity", 4.1), null);
        service.apply(envelope("mid-end", p));

        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        verify(messageRepo, never()).save(any(InterviewMessage.class));
    }

    @Test
    void followup_dropped_when_parent_belongs_to_different_session() {
        InterviewSession s99 = inProgressWith(99L, 1, 10);
        InterviewSession sOther = inProgressWith(77L, 1, 10);
        InterviewMessage parentInOtherSession = intervieweeMessage(sOther, 2, 802L);

        when(processedRepo.existsById(any())).thenReturn(false);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s99));
        when(messageRepo.findById(802L)).thenReturn(Optional.of(parentInOtherSession));

        QuestionsCallbackPayload p = new QuestionsCallbackPayload(
            99L, "FOLLOWUP", "C", "?", 802L, null, null);
        service.apply(envelope("mid-cross", p));

        verify(messageRepo, never()).save(any(InterviewMessage.class));
        verify(processedRepo).save(any());
    }

    /**
     * IN_PROGRESS 세션을 만들고 incrementQuestionCount() 를 count 번 호출해 준다.
     * (markInProgress → IN_PROGRESS 전이 후 count 만큼 증가)
     */
    private InterviewSession inProgressWith(Long sessionId, int count, int max) {
        User user = User.createGithubUser(1L, "u", "u@e.com", "url", "tok");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.ONLINE, InterviewType.TECHNICAL,
            JobCategory.BACKEND, max, 60);
        ReflectionTestUtils.setField(s, "id", sessionId);
        s.markInProgress();
        for (int i = 0; i < count; i++) {
            s.incrementQuestionCount();
        }
        return s;
    }

    private InterviewMessage intervieweeMessage(InterviewSession session, int seq, Long id) {
        InterviewMessage m = InterviewMessage.interviewee(session, seq, "답변입니다.", null);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private QuestionsCallbackEnvelope envelope(String mid, QuestionsCallbackPayload p) {
        return new QuestionsCallbackEnvelope(mid, "callback.questions", "v1",
            "trace-1", Instant.now(), "ai-server", p,
            new MessageContext(1L, 99L, null, null));
    }
}
