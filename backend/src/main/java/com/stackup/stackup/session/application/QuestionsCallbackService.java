package com.stackup.stackup.session.application;

import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.QuestionsCallbackEnvelope;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload.GeneratedQuestion;
import com.stackup.stackup.session.application.event.QuestionPersistedEvent;
import com.stackup.stackup.session.application.event.SessionEndedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionQuestionPool;
import com.stackup.stackup.session.domain.SessionQuestionPoolRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// callback.questions handling.
// POOL is kept as the legacy callback kind, but Core treats it as the single
// initial question result. Extra questions are ignored; Core does not manage a pool.
// FOLLOWUP inserts a follow-up question and pushes it through SSE.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionsCallbackService {

    private static final Logger log = LoggerFactory.getLogger(QuestionsCallbackService.class);
    private static final String CONSUMER_NAME = "core.callback.questions";

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final SessionQuestionPoolRepository poolRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void apply(QuestionsCallbackEnvelope envelope) {
        if (envelope == null || envelope.payload() == null) {
            log.warn("callback.questions envelope or payload is null — skip");
            return;
        }
        QuestionsCallbackPayload payload = envelope.payload();
        if (isProcessed(envelope.messageId())) {
            log.info("callback.questions duplicate, skip. messageId={}", envelope.messageId());
            return;
        }
        Long sessionId = payload.sessionId();
        if (sessionId == null) {
            log.warn("callback.questions missing sessionId. messageId={}", envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isDeleted()) {
            log.warn("callback.questions session not found or deleted. id={}, messageId={}",
                sessionId, envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }

        if (payload.isPool()) {
            applyInitialQuestion(session, payload);
        } else if (payload.isFollowup()) {
            applyFollowup(session, payload);
        } else {
            log.warn("callback.questions unknown kind={}. messageId={}", payload.kind(), envelope.messageId());
        }
        markProcessed(envelope.messageId());
    }

    // POOL 콜백: AI 가 만든 일반질문들을 풀에 저장하고 첫 질문을 삽입한다.
    private void applyInitialQuestion(InterviewSession session, QuestionsCallbackPayload payload) {
        List<GeneratedQuestion> questions = payload.questions();
        if (questions == null || questions.isEmpty()) {
            log.warn("callback.questions initial result with no questions. sessionId={}", session.getId());
            return;
        }
        if (poolRepository.countBySessionId(session.getId()) > 0) {
            log.info("callback.questions pool already seeded, skip. sessionId={}", session.getId());
            return;
        }
        int idx = 0;
        for (GeneratedQuestion q : questions) {
            poolRepository.save(SessionQuestionPool.of(
                session.getId(), idx++, q.question(), q.category(), q.targetEvidence(), q.expectedSignal()));
        }
        poolRepository.findFirstBySessionIdAndUsedFalseOrderByIdxAsc(session.getId())
            .ifPresent(first -> insertGeneralFromPool(session, first, "INITIAL_QUESTION_READY"));
        log.info("callback.questions pool seeded. sessionId={}, poolSize={}", session.getId(), questions.size());
    }

    // 풀에서 다음 일반질문을 꺼내 삽입(꼬리질문 m개 소진 후 호출). 없으면 종료.
    @Transactional
    public void advanceToNextGeneral(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != SessionStatus.IN_PROGRESS) {
            return;
        }
        if (session.isMaxReached()) {
            endSession(session, "MAX_QUESTIONS_REACHED");
            return;
        }
        poolRepository.findFirstBySessionIdAndUsedFalseOrderByIdxAsc(sessionId).ifPresentOrElse(
            next -> insertGeneralFromPool(session, next, "GENERAL_QUESTION_READY"),
            () -> endSession(session, "POOL_EXHAUSTED"));
    }

    private void insertGeneralFromPool(InterviewSession session, SessionQuestionPool pool, String reason) {
        pool.markUsed();
        poolRepository.save(pool);
        long currentMsgs = messageRepository.countBySession_Id(session.getId());
        int nextSeq = (int) currentMsgs + 1;
        InterviewMessage message = messageRepository.save(
            InterviewMessage.interviewer(session, nextSeq, pool.getQuestion(),
                pool.getCategory(), pool.getTargetEvidence(), pool.getExpectedSignal()));
        session.incrementQuestionCount();
        publishQuestionEvents(session, message, reason);
        maybeAutoEnd(session);
    }

    private void publishQuestionEvents(InterviewSession session, InterviewMessage message, String reason) {
        events.publishEvent(new QuestionPersistedEvent(
            session.getUser().getId(), session.getId(), message.getId()));
        events.publishEvent(RealtimeNotifyEvent.session(session.getId(), SseEventType.SESSION_MESSAGE, message.getId()));
        events.publishEvent(RealtimeNotifyEvent.user(session.getUser().getId(), SseEventType.SESSION_MESSAGE,
            new SessionMessageNotice(session.getId(), message.getId(), reason)));
    }

    private void maybeAutoEnd(InterviewSession session) {
        if (session.isMaxReached()) {
            endSession(session, "MAX_QUESTIONS_REACHED");
        }
    }

    private void endSession(InterviewSession session, String reason) {
        try {
            session.end();
            events.publishEvent(RealtimeNotifyEvent.session(session.getId(), SseEventType.SESSION_STATE,
                new SessionStateNotice(session.getId(), session.getStatus().name(), reason)));
            events.publishEvent(RealtimeNotifyEvent.user(session.getUser().getId(), SseEventType.SESSION_STATE,
                new SessionStateNotice(session.getId(), session.getStatus().name(), reason)));
            events.publishEvent(new SessionEndedEvent(session.getUser().getId(), session.getId(), reason));
            log.info("session auto-completed. sessionId={}, reason={}", session.getId(), reason);
        } catch (IllegalStateException e) {
            log.warn("auto-end skipped — session not IN_PROGRESS. sessionId={}, status={}",
                session.getId(), session.getStatus());
        }
    }

    private void applyFollowup(InterviewSession session, QuestionsCallbackPayload payload) {
        InterviewMessage parent = payload.parentMessageId() == null
            ? null
            : messageRepository.findById(payload.parentMessageId()).orElse(null);
        String intent = payload.answerIntent() == null ? "NORMAL" : payload.answerIntent();

        // 모르겠음 → 이 주제 더 안 파고 다음 일반질문으로(없으면 종료). 답변평가는 기록(낮은 점수 의미 있음).
        if ("DONT_KNOW".equalsIgnoreCase(intent)) {
            recordAnswerEvaluation(payload);
            log.info("callback.questions DONT_KNOW — advancing to next general. sessionId={}", session.getId());
            advanceToNextGeneral(session.getId());
            return;
        }

        if (payload.followupQuestion() == null || payload.followupQuestion().isBlank()) {
            log.warn("callback.questions FOLLOWUP empty question. sessionId={}", session.getId());
            return;
        }

        // 부연 요청 → 같은 질문 재설명. 질문 수(k)·깊이(m) 미반영, 답변평가 기록 안 함.
        if ("CLARIFICATION".equalsIgnoreCase(intent)) {
            int seq = (int) messageRepository.countBySession_Id(session.getId()) + 1;
            InterviewMessage clar = messageRepository.save(
                InterviewMessage.clarification(session, seq, payload.followupQuestion(), parent));
            publishQuestionEvents(session, clar, "CLARIFICATION");
            log.info("callback.questions CLARIFICATION — re-explained question. sessionId={}, msg={}",
                session.getId(), clar.getId());
            return;
        }

        // 정상 답변 → 꼬리질문 삽입.
        recordAnswerEvaluation(payload);
        int nextSeq = (int) messageRepository.countBySession_Id(session.getId()) + 1;
        InterviewMessage message = messageRepository.save(
            InterviewMessage.followup(session, nextSeq, payload.followupQuestion(), parent)
        );
        session.incrementQuestionCount();
        publishQuestionEvents(session, message, "FOLLOWUP_READY");

        // maxQuestions(k) 도달 시 자동 종료.
        maybeAutoEnd(session);

        log.info("callback.questions FOLLOWUP processed. sessionId={}, msg={}, totalQ={}",
            session.getId(), message.getId(), session.getTotalQuestionCount());
    }

    public record SessionStateNotice(Long sessionId, String status, String reason) {
    }

    private void recordAnswerEvaluation(QuestionsCallbackPayload payload) {
        QuestionsCallbackPayload.AnswerEvaluation eval = payload.answerEvaluation();
        if (eval == null || payload.answerMessageId() == null) {
            return;
        }
        messageRepository.findById(payload.answerMessageId()).ifPresent(answer -> {
            answer.recordAnswerEvaluation(
                eval.specificity(), eval.logic(), eval.structure(), eval.correctness());
            messageRepository.save(answer);
        });
    }

    private boolean isProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        return processedMessageRepository.existsById(messageId);
    }

    private void markProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        try {
            processedMessageRepository.save(ProcessedMessage.of(messageId, CONSUMER_NAME));
        } catch (DataIntegrityViolationException ignored) {
            // race
        }
    }

    public record SessionMessageNotice(Long sessionId, Long messageId, String reason) {
    }
}
