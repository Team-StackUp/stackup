package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.QuestionsCallbackEnvelope;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload.GeneratedQuestion;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
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
class QuestionsCallbackServiceTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock ProcessedMessageRepository processedMessageRepository;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @InjectMocks QuestionsCallbackService service;

    @Test
    void apply_poolCallbackStoresOnlyInitialQuestionAndPushesSse() {
        InterviewSession session = sessionFixture(11L, SessionStatus.READY);
        QuestionsCallbackEnvelope env = poolEnvelope(11L, List.of(
            new GeneratedQuestion("PROJECT_DEEP_DIVE", "Introduce yourself",
                "이력서: 결제 시스템", "협업/문제해결 깊이"),
            new GeneratedQuestion("TECH", "JPA?", null, null)
        ));
        when(processedMessageRepository.existsById("m-1")).thenReturn(false);
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 500L);
            return m;
        });

        service.apply(env);

        ArgumentCaptor<InterviewMessage> messageCaptor = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("Introduce yourself");
        assertThat(messageCaptor.getValue().getSequenceNumber()).isEqualTo(1);
        // 질문 메타데이터가 첫 질문에서 영속됨
        assertThat(messageCaptor.getValue().getCategory()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(messageCaptor.getValue().getTargetEvidence()).isEqualTo("이력서: 결제 시스템");
        assertThat(messageCaptor.getValue().getExpectedSignal()).isEqualTo("협업/문제해결 깊이");

        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues()).anySatisfy(e -> {
            assertThat(e).isInstanceOf(RealtimeNotifyEvent.class);
            RealtimeNotifyEvent rne = (RealtimeNotifyEvent) e;
            assertThat(rne.channel()).isEqualTo(RealtimeNotifyEvent.Channel.SESSION);
            assertThat(rne.type()).isEqualTo(SseEventType.SESSION_MESSAGE);
        });
        assertThat(ev.getAllValues()).anySatisfy(e -> {
            assertThat(e).isInstanceOf(RealtimeNotifyEvent.class);
            RealtimeNotifyEvent rne = (RealtimeNotifyEvent) e;
            assertThat(rne.channel()).isEqualTo(RealtimeNotifyEvent.Channel.USER);
            assertThat(rne.payload()).isInstanceOf(QuestionsCallbackService.SessionMessageNotice.class);
            assertThat(((QuestionsCallbackService.SessionMessageNotice) rne.payload()).reason())
                .isEqualTo("INITIAL_QUESTION_READY");
        });
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
        assertThat(session.getTotalQuestionCount()).isEqualTo(1);
    }

    @Test
    void apply_followupAutoEndsSessionAtMaxQuestions() {
        InterviewSession session = sessionFixture(11L, SessionStatus.IN_PROGRESS);
        // maxQuestions=5; total=4, then one follow-up reaches the limit.
        ReflectionTestUtils.setField(session, "totalQuestionCount", 4);

        QuestionsCallbackEnvelope env = followupEnvelope(11L, 200L, "Follow-up?");
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
        QuestionsCallbackEnvelope env =
            poolEnvelope(11L, List.of(new GeneratedQuestion("X", "Q", null, null)));
        when(processedMessageRepository.existsById("m-1")).thenReturn(true);

        service.apply(env);

        verify(sessionRepository, never()).findById(any());
        verify(messageRepository, never()).save(any(InterviewMessage.class));
    }

    @Test
    void apply_followupPersistsAnswerEvaluationOntoAnswerMessage() {
        InterviewSession session = sessionFixture(11L, SessionStatus.IN_PROGRESS);
        InterviewMessage answer =
            InterviewMessage.interviewee(session, 2, "내 답변", null, "idem-1");
        ReflectionTestUtils.setField(answer, "id", 600L);

        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            11L, "FOLLOWUP", null, 500L, 600L, "다음 질문?",
            new QuestionsCallbackPayload.AnswerEvaluation(2.0, 3.0, "PARTIAL_STAR", 1.5)
        );
        QuestionsCallbackEnvelope env = new QuestionsCallbackEnvelope(
            "m-eval", "callback.questions", "1", "t", null, "ai", payload, null);

        when(processedMessageRepository.existsById("m-eval")).thenReturn(false);
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(messageRepository.findById(500L)).thenReturn(Optional.empty());
        when(messageRepository.findById(600L)).thenReturn(Optional.of(answer));
        when(messageRepository.countBySession_Id(11L)).thenReturn(2L);
        when(messageRepository.save(any(InterviewMessage.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.apply(env);

        assertThat(answer.getAnswerSpecificity()).isEqualTo(2.0);
        assertThat(answer.getAnswerLogic()).isEqualTo(3.0);
        assertThat(answer.getAnswerStructure()).isEqualTo("PARTIAL_STAR");
        assertThat(answer.getAnswerCorrectness()).isEqualTo(1.5);
    }

    private QuestionsCallbackEnvelope poolEnvelope(Long sessionId, List<GeneratedQuestion> questions) {
        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            sessionId, "POOL", questions, null, null, null, null
        );
        return new QuestionsCallbackEnvelope("m-1", "callback.questions", "1", "t", null, "ai", payload, null);
    }

    private QuestionsCallbackEnvelope followupEnvelope(Long sessionId, Long parentId, String followup) {
        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            sessionId, "FOLLOWUP", null, parentId, null, followup, null
        );
        return new QuestionsCallbackEnvelope("m-2", "callback.questions", "1", "t", null, "ai", payload, null);
    }

    private InterviewSession sessionFixture(Long id, SessionStatus status) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30
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
