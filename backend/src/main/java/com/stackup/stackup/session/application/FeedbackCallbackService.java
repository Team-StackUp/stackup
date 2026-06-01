package com.stackup.stackup.session.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.messaging.RealtimeNotifyPublisher;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.FeedbackCallbackEnvelope;
import com.stackup.stackup.session.application.dto.FeedbackCallbackPayload;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionFeedback;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// callback.feedback 처리 — SessionFeedback INSERT + SSE session.feedback push.
// 멱등: processed_messages (envelope messageId) + session_feedbacks UNIQUE (session_id).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackCallbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackCallbackService.class);
    private static final String CONSUMER_NAME = "core.callback.feedback";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final InterviewSessionRepository sessionRepository;
    private final SessionFeedbackRepository feedbackRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final RealtimeNotifyPublisher realtimeNotifyPublisher;

    @Transactional
    public void apply(FeedbackCallbackEnvelope envelope) {
        if (envelope == null || envelope.payload() == null) {
            log.warn("callback.feedback envelope or payload is null — skip");
            return;
        }
        if (isProcessed(envelope.messageId())) {
            log.info("callback.feedback duplicate, skip. messageId={}", envelope.messageId());
            return;
        }
        FeedbackCallbackPayload payload = envelope.payload();
        Long sessionId = payload.sessionId();
        if (sessionId == null) {
            log.warn("callback.feedback missing sessionId. messageId={}", envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isDeleted()) {
            log.warn("callback.feedback session not found or deleted. id={}, messageId={}",
                sessionId, envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }
        if (feedbackRepository.existsBySession_Id(sessionId)) {
            log.info("callback.feedback feedback already exists. sessionId={}", sessionId);
            markProcessed(envelope.messageId());
            return;
        }

        SessionFeedback feedback = SessionFeedback.of(
            session,
            payload.overallScore(),
            payload.technicalAccuracy(),
            payload.logicScore(),
            payload.communicationScore(),
            payload.strengthsSummary(),
            payload.weaknessesSummary(),
            keywordsToJson(payload.improvementKeywords()),
            payload.reportS3Key()
        );
        try {
            feedback = feedbackRepository.save(feedback);
        } catch (DataIntegrityViolationException race) {
            log.info("callback.feedback unique race — feedback inserted concurrently. sessionId={}", sessionId);
            markProcessed(envelope.messageId());
            return;
        }

        realtimeNotifyPublisher.publishToSession(sessionId, SseEventType.FEEDBACK_READY,
            new SessionFeedbackNotice(sessionId, feedback.getId()));
        realtimeNotifyPublisher.publishToUser(session.getUser().getId(), SseEventType.FEEDBACK_READY,
            new SessionFeedbackNotice(sessionId, feedback.getId()));

        markProcessed(envelope.messageId());
        log.info("callback.feedback processed. sessionId={}, feedbackId={}", sessionId, feedback.getId());
    }

    public record SessionFeedbackNotice(Long sessionId, Long feedbackId) {
    }

    private String keywordsToJson(java.util.List<String> keywords) {
        if (keywords == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(keywords);
        } catch (JsonProcessingException e) {
            log.warn("improvement_keywords json serialize failed", e);
            return "[]";
        }
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
}
