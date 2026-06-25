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
    @Mock com.stackup.stackup.session.domain.SessionQuestionPoolRepository poolRepository;
    @Mock ProcessedMessageRepository processedMessageRepository;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @InjectMocks QuestionsCallbackService service;

    @Test
    void apply_poolCallbackSeedsPoolAndInsertsFirstQuestion() {
        InterviewSession session = sessionFixture(11L, SessionStatus.READY);
        QuestionsCallbackEnvelope env = poolEnvelope(11L, List.of(
            new GeneratedQuestion("PROJECT_DEEP_DIVE", "Introduce yourself",
                "BACKEND", "이력서: 결제 시스템", "협업/문제해결 깊이"),
            new GeneratedQuestion("TECH", "JPA?", null, null, null)
        ));
        when(processedMessageRepository.existsById("m-1")).thenReturn(false);
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(poolRepository.countBySessionId(11L)).thenReturn(0L);
        // 풀 저장 후 첫(미사용) 질문을 꺼내 일반질문으로 삽입.
        when(poolRepository.findFirstBySessionIdAndUsedFalseOrderByIdxAsc(11L)).thenReturn(
            Optional.of(com.stackup.stackup.session.domain.SessionQuestionPool.of(
                11L, 0, "Introduce yourself", "PROJECT_DEEP_DIVE",
                "BACKEND", "이력서: 결제 시스템", "협업/문제해결 깊이")));
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
    void insertSelfIntroduction_insertsFixedFirstQuestionAndCounts() {
        InterviewSession session = sessionFixture(11L, SessionStatus.READY);
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(messageRepository.countBySession_Id(11L)).thenReturn(0L);
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> {
            InterviewMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 400L);
            return m;
        });

        service.insertSelfIntroduction(11L);

        ArgumentCaptor<InterviewMessage> cap = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(messageRepository).save(cap.capture());
        assertThat(cap.getValue().getContent()).isEqualTo(InterviewMessage.SELF_INTRODUCTION_TEXT);
        assertThat(cap.getValue().getSequenceNumber()).isEqualTo(1);
        assertThat(cap.getValue().isSelfIntroduction()).isTrue();
        // 자기소개는 첫 일반질문 1자리를 차지한다.
        assertThat(session.getTotalQuestionCount()).isEqualTo(1);
    }

    @Test
    void insertSelfIntroduction_skipsWhenMessagesAlreadyExist() {
        InterviewSession session = sessionFixture(11L, SessionStatus.READY);
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(messageRepository.countBySession_Id(11L)).thenReturn(1L);

        service.insertSelfIntroduction(11L);

        verify(messageRepository, never()).save(any(InterviewMessage.class));
        assertThat(session.getTotalQuestionCount()).isEqualTo(0);
    }

    @Test
    void apply_followupDoesNotCountOrAutoEnd() {
        InterviewSession session = sessionFixture(11L, SessionStatus.IN_PROGRESS);
        // 꼬리질문은 maxQuestions(메인질문 수) 한도에 포함되지 않으며, 도착해도 세션을
        // 끝내지 않는다(종료는 메인질문의 꼬리 사이클 후 advanceToNextGeneral 에서만).
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

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.getTotalQuestionCount()).isEqualTo(4);
    }

    @Test
    void apply_skipsDuplicateMessageId() {
        QuestionsCallbackEnvelope env =
            poolEnvelope(11L, List.of(new GeneratedQuestion("X", "Q", null, null, null)));
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
            new QuestionsCallbackPayload.AnswerEvaluation(2.0, 3.0, "PARTIAL_STAR", 1.5), "NORMAL", null
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

    @Test
    void apply_clarificationReExplainsWithoutCountingQuestion() {
        InterviewSession session = sessionFixture(11L, SessionStatus.IN_PROGRESS);
        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            11L, "FOLLOWUP", null, 500L, 600L, "쉽게 다시 설명: 트랜잭션이란…",
            null, "CLARIFICATION", null
        );
        QuestionsCallbackEnvelope env = new QuestionsCallbackEnvelope(
            "m-clar", "callback.questions", "1", "t", null, "ai", payload, null);

        when(processedMessageRepository.existsById("m-clar")).thenReturn(false);
        when(sessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(messageRepository.findById(500L)).thenReturn(Optional.empty());
        when(messageRepository.countBySession_Id(11L)).thenReturn(2L);
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.apply(env);

        ArgumentCaptor<InterviewMessage> cap = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(messageRepository).save(cap.capture());
        assertThat(cap.getValue().isClarification()).isTrue();
        // 부연은 질문 수(k)에 카운트되지 않는다.
        assertThat(session.getTotalQuestionCount()).isEqualTo(0);
    }

    // ── 새 placeholder 경로 테스트 ─────────────────────────────────────────────

    @Test
    void apply_followupNormal_updatesPlaceholderInPlaceWithoutCounting() {
        InterviewSession session = sessionFixture(20L, SessionStatus.IN_PROGRESS);
        // placeholder: followupPlaceholder 의 sentinel content
        InterviewMessage placeholder = InterviewMessage.followupPlaceholder(
            session, 3, parentMessageFixture(session));
        ReflectionTestUtils.setField(placeholder, "id", 301L);

        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            20L, "FOLLOWUP", null, 200L, null, "꼬리질문 내용?",
            null, "NORMAL", 301L
        );
        QuestionsCallbackEnvelope env = new QuestionsCallbackEnvelope(
            "m-ph-normal", "callback.questions", "1", "t", null, "ai", payload, null);

        when(processedMessageRepository.existsById("m-ph-normal")).thenReturn(false);
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(parentMessageFixture(session)));
        when(messageRepository.findById(301L)).thenReturn(Optional.of(placeholder));
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.apply(env);

        // placeholder 가 in-place 로 업데이트되어야 함
        assertThat(placeholder.getContent()).isEqualTo("꼬리질문 내용?");
        assertThat(placeholder.getStatus()).isEqualTo(com.stackup.stackup.session.domain.MessageStatus.COMPLETED);
        // 꼬리질문은 메인질문 카운트(maxQuestions 한도)에 포함되지 않는다.
        assertThat(session.getTotalQuestionCount()).isEqualTo(0);
        // save 는 placeholder 자체에 대해 호출됨 (새 InterviewMessage INSERT 아님)
        ArgumentCaptor<InterviewMessage> cap = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(messageRepository).save(cap.capture());
        assertThat(cap.getValue()).isSameAs(placeholder);
    }

    @Test
    void apply_followupClarification_updatesPlaceholderWithoutCounting() {
        InterviewSession session = sessionFixture(21L, SessionStatus.IN_PROGRESS);
        InterviewMessage placeholder = InterviewMessage.followupPlaceholder(
            session, 3, parentMessageFixture(session));
        ReflectionTestUtils.setField(placeholder, "id", 302L);

        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            21L, "FOLLOWUP", null, 200L, null, "다시 설명드리면…",
            null, "CLARIFICATION", 302L
        );
        QuestionsCallbackEnvelope env = new QuestionsCallbackEnvelope(
            "m-ph-clar", "callback.questions", "1", "t", null, "ai", payload, null);

        when(processedMessageRepository.existsById("m-ph-clar")).thenReturn(false);
        when(sessionRepository.findById(21L)).thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(parentMessageFixture(session)));
        when(messageRepository.findById(302L)).thenReturn(Optional.of(placeholder));
        when(messageRepository.save(any(InterviewMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        service.apply(env);

        // clarification 플래그가 세팅되어야 함
        assertThat(placeholder.isClarification()).isTrue();
        assertThat(placeholder.getContent()).isEqualTo("다시 설명드리면…");
        // 질문 수 카운트 미증가
        assertThat(session.getTotalQuestionCount()).isEqualTo(0);
    }

    @Test
    void apply_followupDontKnow_deletesPlaceholderAndAdvances() {
        InterviewSession session = sessionFixture(22L, SessionStatus.IN_PROGRESS);
        InterviewMessage placeholder = InterviewMessage.followupPlaceholder(
            session, 3, parentMessageFixture(session));
        ReflectionTestUtils.setField(placeholder, "id", 303L);

        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            22L, "FOLLOWUP", null, 200L, null, null,
            null, "DONT_KNOW", 303L
        );
        QuestionsCallbackEnvelope env = new QuestionsCallbackEnvelope(
            "m-ph-dk", "callback.questions", "1", "t", null, "ai", payload, null);

        when(processedMessageRepository.existsById("m-ph-dk")).thenReturn(false);
        when(sessionRepository.findById(22L)).thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(parentMessageFixture(session)));
        when(messageRepository.findById(303L)).thenReturn(Optional.of(placeholder));
        // advanceToNextGeneral 내부에서 sessionRepository.findById 재호출
        when(poolRepository.findFirstBySessionIdAndUsedFalseOrderByIdxAsc(22L))
            .thenReturn(java.util.Optional.empty());

        service.apply(env);

        // placeholder 가 삭제되어야 함
        verify(messageRepository).delete(placeholder);
        verify(messageRepository).flush();
        // 세션이 종료됨 (풀이 비어있어 POOL_EXHAUSTED)
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void advanceToNextGeneral_endsWithMaxReached_whenMainQuestionsHitLimit() {
        InterviewSession session = sessionFixture(33L, SessionStatus.IN_PROGRESS);
        // maxQuestions=5; 메인질문 5개를 이미 던진 상태 → 다음 advance 에서 풀을 보지 않고 종료.
        ReflectionTestUtils.setField(session, "totalQuestionCount", 5);
        when(sessionRepository.findById(33L)).thenReturn(Optional.of(session));

        service.advanceToNextGeneral(33L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        verify(poolRepository, never()).findFirstBySessionIdAndUsedFalseOrderByIdxAsc(any());
    }

    private QuestionsCallbackEnvelope poolEnvelope(Long sessionId, List<GeneratedQuestion> questions) {
        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            sessionId, "POOL", questions, null, null, null, null, null, null
        );
        return new QuestionsCallbackEnvelope("m-1", "callback.questions", "1", "t", null, "ai", payload, null);
    }

    private QuestionsCallbackEnvelope followupEnvelope(Long sessionId, Long parentId, String followup) {
        QuestionsCallbackPayload payload = new QuestionsCallbackPayload(
            sessionId, "FOLLOWUP", null, parentId, null, followup, null, "NORMAL", null
        );
        return new QuestionsCallbackEnvelope("m-2", "callback.questions", "1", "t", null, "ai", payload, null);
    }

    private InterviewSession sessionFixture(Long id, SessionStatus status) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, null, null
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
