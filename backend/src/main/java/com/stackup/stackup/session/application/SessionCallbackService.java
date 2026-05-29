package com.stackup.stackup.session.application;

import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.QuestionsCallbackEnvelope;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionCallbackService {

    private static final Logger log = LoggerFactory.getLogger(SessionCallbackService.class);
    private static final String CONSUMER_NAME = "core.callback.questions";

    private final InterviewSessionRepository sessionRepo;
    private final InterviewMessageRepository messageRepo;
    private final ProcessedMessageRepository processedRepo;
    private final SseEventPublisher sse;

    @Transactional
    public void apply(QuestionsCallbackEnvelope envelope) {
        if (envelope == null || envelope.payload() == null) {
            log.warn("callback.questions envelope or payload is null — skip");
            return;
        }
        if (isProcessed(envelope.messageId())) {
            log.info("callback.questions duplicate, skip. messageId={}", envelope.messageId());
            return;
        }
        QuestionsCallbackPayload p = envelope.payload();
        log.info("callback.questions received. messageId={}, sessionId={}, kind={}, traceId={}",
            envelope.messageId(), p.sessionId(), p.kind(), envelope.traceId());

        if (p.isFirst()) {
            applyFirstQuestion(p);
        } else if (p.isFollowup()) {
            applyFollowup(p); // Task G1 에서 구현
        } else if (p.isEnd()) {
            applyEnd(p);      // Task G1 에서 구현
        } else {
            log.warn("unknown kind={}, skip. messageId={}", p.kind(), envelope.messageId());
        }
        markProcessed(envelope.messageId());
    }

    private void applyFirstQuestion(QuestionsCallbackPayload p) {
        InterviewSession session = sessionRepo.findByIdAndIsDeletedFalse(p.sessionId()).orElse(null);
        if (session == null) {
            log.warn("session not found. id={}", p.sessionId());
            return;
        }
        if (session.getStatus() != SessionStatus.READY) {
            log.warn("session not READY, drop FIRST. id={}, status={}",
                session.getId(), session.getStatus());
            return;
        }
        if (p.question() == null || p.question().isBlank()) {
            log.warn("first question is blank. sessionId={}", p.sessionId());
            sse.publishToSession(session.getId(), SseEventType.ERROR,
                Map.of("code", "FIRST_QUESTION_EMPTY",
                       "message", "첫 질문 생성에 실패했습니다."));
            return;
        }

        session.markInProgress();

        int nextSeq = messageRepo.findMaxSequenceBySessionId(session.getId()) + 1;
        InterviewMessage msg = messageRepo.save(
            InterviewMessage.interviewer(session, nextSeq, p.question(), null));
        session.incrementQuestionCount();

        sse.publishToSession(session.getId(), SseEventType.SESSION_MESSAGE,
            MessageResult.from(msg));
        sse.publishToSession(session.getId(), SseEventType.SESSION_STATE,
            Map.of("sessionId", session.getId(), "state", session.getStatus().name(),
                   "totalQuestionCount", session.getTotalQuestionCount()));
    }

    private void applyFollowup(QuestionsCallbackPayload p) {
        InterviewSession session = sessionRepo.findByIdAndIsDeletedFalse(p.sessionId()).orElse(null);
        if (session == null) {
            log.warn("session not found. id={}", p.sessionId());
            return;
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            log.warn("session not in progress, drop followup. id={}, status={}",
                session.getId(), session.getStatus());
            return;
        }
        if (p.parentMessageId() == null) {
            log.warn("followup missing parentMessageId. sessionId={}", session.getId());
            return;
        }
        if (p.question() == null || p.question().isBlank()) {
            log.warn("followup question blank. sessionId={}", session.getId());
            return;
        }

        InterviewMessage parent = messageRepo.findById(p.parentMessageId()).orElse(null);
        if (parent == null) {
            log.warn("followup parent not found. id={}", p.parentMessageId());
            return;
        }
        if (!parent.getSession().getId().equals(session.getId())) {
            log.warn("followup parent belongs to different session. parentId={}, parent.sessionId={}, expected.sessionId={}",
                parent.getId(), parent.getSession().getId(), session.getId());
            return;
        }

        int nextSeq = messageRepo.findMaxSequenceBySessionId(session.getId()) + 1;
        InterviewMessage q = messageRepo.save(
            InterviewMessage.interviewer(session, nextSeq, p.question(), parent));
        session.incrementQuestionCount();

        sse.publishToSession(session.getId(), SseEventType.SESSION_MESSAGE,
            MessageResult.from(q));

        if (session.isMaxReached()) {
            session.end();
            sse.publishToSession(session.getId(), SseEventType.SESSION_STATE,
                Map.of("sessionId", session.getId(), "state", session.getStatus().name(),
                       "totalQuestionCount", session.getTotalQuestionCount(),
                       "endedAt", session.getEndedAt()));
        } else {
            sse.publishToSession(session.getId(), SseEventType.SESSION_STATE,
                Map.of("sessionId", session.getId(), "state", session.getStatus().name(),
                       "totalQuestionCount", session.getTotalQuestionCount()));
        }
    }

    private void applyEnd(QuestionsCallbackPayload p) {
        InterviewSession session = sessionRepo.findByIdAndIsDeletedFalse(p.sessionId()).orElse(null);
        if (session == null) {
            log.warn("session not found. id={}", p.sessionId());
            return;
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            log.warn("session not in progress, drop END. id={}, status={}",
                session.getId(), session.getStatus());
            return;
        }
        session.end();
        sse.publishToSession(session.getId(), SseEventType.SESSION_STATE,
            Map.of("sessionId", session.getId(), "state", session.getStatus().name(),
                   "totalQuestionCount", session.getTotalQuestionCount(),
                   "endedAt", session.getEndedAt()));
    }

    private boolean isProcessed(String messageId) {
        return messageId != null && !messageId.isBlank() && processedRepo.existsById(messageId);
    }

    private void markProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) return;
        try {
            processedRepo.save(ProcessedMessage.of(messageId, CONSUMER_NAME));
        } catch (DataIntegrityViolationException ignored) {
            // race: 무시 (AnalysisCallbackService 와 동일 패턴)
        }
    }
}
