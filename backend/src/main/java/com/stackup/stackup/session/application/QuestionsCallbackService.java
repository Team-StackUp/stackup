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
import com.stackup.stackup.session.domain.MessageRole;
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

    // 세션 생성 직후 호출: 모든 면접의 첫 질문인 자기소개 질문(seq=1)을 AI 없이 고정 삽입한다.
    // 이력서/레포 기반 질문 풀은 이 자기소개 답변을 받은 뒤에 생성된다(SessionQuestionsRequester).
    @Transactional
    public void insertSelfIntroduction(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isDeleted()) {
            log.warn("self-intro insert skipped — session not found or deleted. id={}", sessionId);
            return;
        }
        // 멱등: 이미 메시지가 있으면(중복 이벤트) skip.
        if (messageRepository.countBySession_Id(sessionId) > 0) {
            log.info("self-intro insert skipped — messages already exist. sessionId={}", sessionId);
            return;
        }
        InterviewMessage message = messageRepository.save(InterviewMessage.selfIntroduction(session, 1));
        session.incrementQuestionCount();
        publishQuestionEvents(session, message, "SELF_INTRO_READY");
        log.info("self-intro question inserted. sessionId={}, msg={}", sessionId, message.getId());
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
        String fallbackJobCategory = session.getJobCategory().name();
        for (GeneratedQuestion q : questions) {
            String jobCategory = (q.jobCategory() != null && !q.jobCategory().isBlank())
                ? q.jobCategory() : fallbackJobCategory;
            poolRepository.save(SessionQuestionPool.of(
                session.getId(), idx++, q.question(), q.category(), jobCategory,
                q.targetEvidence(), q.expectedSignal()));
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
        // 여기서 자동종료하지 않는다 — 방금 던진 메인질문은 답변·꼬리질문을 거쳐야 한다.
        // maxQuestions 도달 종료는 꼬리 사이클 후 advanceToNextGeneral 에서 판정한다.
    }

    private void publishQuestionEvents(InterviewSession session, InterviewMessage message, String reason) {
        events.publishEvent(new QuestionPersistedEvent(
            session.getUser().getId(), session.getId(), message.getId()));
        events.publishEvent(RealtimeNotifyEvent.session(session.getId(), SseEventType.SESSION_MESSAGE, message.getId()));
        events.publishEvent(RealtimeNotifyEvent.user(session.getUser().getId(), SseEventType.SESSION_MESSAGE,
            new SessionMessageNotice(session.getId(), message.getId(), reason)));
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

        InterviewMessage placeholder = payload.followupMessageId() == null
            ? null
            : messageRepository.findById(payload.followupMessageId()).orElse(null);

        // 모르겠음 → 이 주제 그만, 다음 일반질문. placeholder 는 삭제(seq 연속성 유지).
        if ("DONT_KNOW".equalsIgnoreCase(intent)) {
            recordAnswerEvaluation(payload);
            if (placeholder != null) {
                messageRepository.delete(placeholder);
                messageRepository.flush();
            }
            log.info("callback.questions DONT_KNOW — advancing to next general. sessionId={}", session.getId());
            advanceToNextGeneral(session.getId());
            return;
        }

        if (payload.followupQuestion() == null || payload.followupQuestion().isBlank()) {
            log.warn("callback.questions FOLLOWUP empty question. sessionId={}", session.getId());
            return;
        }

        boolean clarification = "CLARIFICATION".equalsIgnoreCase(intent);
        if (!clarification) {
            recordAnswerEvaluation(payload);
        }

        InterviewMessage message;
        if (placeholder != null) {
            placeholder.completeFollowup(payload.followupQuestion(), clarification);
            message = messageRepository.save(placeholder);
        } else {
            // 레거시 폴백: placeholder 없이 도착한 콜백(롤아웃 호환).
            int nextSeq = (int) messageRepository.countBySession_Id(session.getId()) + 1;
            message = messageRepository.save(clarification
                ? InterviewMessage.clarification(session, nextSeq, payload.followupQuestion(), parent)
                : InterviewMessage.followup(session, nextSeq, payload.followupQuestion(), parent));
        }

        // 꼬리질문은 maxQuestions(메인질문 수) 카운트에 포함하지 않고, 여기서 자동종료하지도
        // 않는다 — 종료는 메인질문의 꼬리 사이클이 끝나고 advanceToNextGeneral 시점에서만 일어나야
        // 답변 직후 생성된 꼬리질문을 버리고 갑자기 피드백으로 튕기는 일이 없다.
        publishQuestionEvents(session, message, clarification ? "CLARIFICATION" : "FOLLOWUP_READY");
        log.info("callback.questions FOLLOWUP processed. sessionId={}, msg={}, clarification={}",
            session.getId(), message.getId(), clarification);
    }

    public record SessionStateNotice(Long sessionId, String status, String reason) {
    }

    private void recordAnswerEvaluation(QuestionsCallbackPayload payload) {
        QuestionsCallbackPayload.AnswerEvaluation eval = payload.answerEvaluation();
        if (eval == null || payload.answerMessageId() == null) {
            return;
        }
        messageRepository.findById(payload.answerMessageId())
            // 답변(INTERVIEWEE) 메시지에만 평가 기록 — 잘못된 messageId 로 질문에 점수가 달리는 것 방지.
            .filter(answer -> answer.getRole() == MessageRole.INTERVIEWEE)
            .ifPresent(answer -> {
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
