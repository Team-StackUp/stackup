package com.stackup.stackup.session.application;

import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.QuestionsCallbackEnvelope;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload.GeneratedQuestion;
import com.stackup.stackup.session.application.event.SessionEndedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
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
    private final ProcessedMessageRepository processedMessageRepository;
    private final SseEventPublisher sseEventPublisher;
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

    private void applyInitialQuestion(InterviewSession session, QuestionsCallbackPayload payload) {
        List<GeneratedQuestion> questions = payload.questions();
        if (questions == null || questions.isEmpty()) {
            log.warn("callback.questions initial result with no questions. sessionId={}", session.getId());
            return;
        }
        GeneratedQuestion first = questions.get(0);
        if (questions.size() > 1) {
            log.info("callback.questions initial result included extra questions; ignoring extras. sessionId={}, extra={}",
                session.getId(), questions.size() - 1);
        }
        InterviewMessage message = messageRepository.save(
            InterviewMessage.interviewer(session, 1, first.question())
        );
        session.incrementQuestionCount();
        sseEventPublisher.publishToSession(session.getId(), SseEventType.SESSION_MESSAGE, message.getId());
        // 사용자 user 채널에도 알림 — frontend 가 documentId/sessionId 사전 인지 없이도 받을 수 있게
        sseEventPublisher.publishToUser(
            session.getUser().getId(),
            SseEventType.SESSION_MESSAGE,
            new SessionMessageNotice(session.getId(), message.getId(), "INITIAL_QUESTION_READY")
        );
        log.info("callback.questions initial question processed. sessionId={}, ignored_extras={}",
            session.getId(), Math.max(questions.size() - 1, 0));
    }

    private void applyFollowup(InterviewSession session, QuestionsCallbackPayload payload) {
        if (payload.followupQuestion() == null || payload.followupQuestion().isBlank()) {
            log.warn("callback.questions FOLLOWUP empty question. sessionId={}", session.getId());
            return;
        }
        InterviewMessage parent = payload.parentMessageId() == null
            ? null
            : messageRepository.findById(payload.parentMessageId()).orElse(null);

        long currentMsgs = messageRepository.countBySession_Id(session.getId());
        int nextSeq = (int) currentMsgs + 1;

        InterviewMessage message = messageRepository.save(
            InterviewMessage.followup(session, nextSeq, payload.followupQuestion(), parent)
        );
        session.incrementQuestionCount();

        sseEventPublisher.publishToSession(session.getId(), SseEventType.SESSION_MESSAGE, message.getId());
        sseEventPublisher.publishToUser(
            session.getUser().getId(),
            SseEventType.SESSION_MESSAGE,
            new SessionMessageNotice(session.getId(), message.getId(), "FOLLOWUP_READY")
        );

        // maxQuestions 도달 시 자동 종료 (plan §A-4)
        Integer max = session.getMaxQuestions();
        if (max != null && session.getTotalQuestionCount() != null
            && session.getTotalQuestionCount() >= max) {
            try {
                session.end();
                sseEventPublisher.publishToSession(session.getId(), SseEventType.SESSION_STATE,
                    new SessionStateNotice(session.getId(), session.getStatus().name(), "MAX_QUESTIONS_REACHED"));
                sseEventPublisher.publishToUser(session.getUser().getId(), SseEventType.SESSION_STATE,
                    new SessionStateNotice(session.getId(), session.getStatus().name(), "MAX_QUESTIONS_REACHED"));
                events.publishEvent(new SessionEndedEvent(
                    session.getUser().getId(), session.getId(), "MAX_QUESTIONS_REACHED"));
                log.info("session auto-completed on max questions. sessionId={}, max={}",
                    session.getId(), max);
            } catch (IllegalStateException e) {
                log.warn("auto-end skipped — session not IN_PROGRESS. sessionId={}, status={}",
                    session.getId(), session.getStatus());
            }
        }

        log.info("callback.questions FOLLOWUP processed. sessionId={}, msg={}, totalQ={}",
            session.getId(), message.getId(), session.getTotalQuestionCount());
    }

    public record SessionStateNotice(Long sessionId, String status, String reason) {
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
