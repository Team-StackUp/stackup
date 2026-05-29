package com.stackup.stackup.session.application;

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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// callback.questions 처리.
// POOL: 첫 질문을 interview_messages 에 INSERT + SSE session.message push.
//       (전체 풀은 first cut 에선 메모리상 처리 — 후속 PR 에서 별도 테이블 또는 컬럼)
// FOLLOWUP: parent_message_id 매핑하여 INSERT + SSE push (B5 진행 시 활성)
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
            applyPool(session, payload);
        } else if (payload.isFollowup()) {
            applyFollowup(session, payload);
        } else {
            log.warn("callback.questions unknown kind={}. messageId={}", payload.kind(), envelope.messageId());
        }
        markProcessed(envelope.messageId());
    }

    private void applyPool(InterviewSession session, QuestionsCallbackPayload payload) {
        List<GeneratedQuestion> questions = payload.questions();
        if (questions == null || questions.isEmpty()) {
            log.warn("callback.questions POOL with no questions. sessionId={}", session.getId());
            return;
        }
        GeneratedQuestion first = questions.get(0);
        InterviewMessage message = messageRepository.save(
            InterviewMessage.interviewer(session, 1, first.question())
        );
        session.incrementQuestionCount();
        sseEventPublisher.publishToSession(session.getId(), SseEventType.SESSION_MESSAGE, message.getId());
        // 사용자 user 채널에도 알림 — frontend 가 documentId/sessionId 사전 인지 없이도 받을 수 있게
        sseEventPublisher.publishToUser(
            session.getUser().getId(),
            SseEventType.SESSION_MESSAGE,
            new SessionMessageNotice(session.getId(), message.getId(), "QUESTION_POOL_READY")
        );
        log.info("callback.questions POOL processed. sessionId={}, total={}",
            session.getId(), questions.size());
    }

    private void applyFollowup(InterviewSession session, QuestionsCallbackPayload payload) {
        // B5 진행 시 활성화. 지금은 envelope 만 로깅 (메시지/답변 시퀀스가 들어와야 의미 있음)
        log.info("callback.questions FOLLOWUP received. sessionId={}, parent={}, willStoreInB5",
            session.getId(), payload.parentMessageId());
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
