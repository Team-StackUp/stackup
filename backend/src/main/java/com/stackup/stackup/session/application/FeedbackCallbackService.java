package com.stackup.stackup.session.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.sse.SessionErrorNotice;
import com.stackup.stackup.common.sse.SessionErrorNotifier;
import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import org.springframework.context.ApplicationEventPublisher;
import com.stackup.stackup.session.application.dto.AnswerCoachingItem;
import com.stackup.stackup.session.application.dto.FeedbackCallbackEnvelope;
import com.stackup.stackup.session.application.dto.FeedbackCallbackPayload;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.MessageRole;
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
    private final InterviewMessageRepository messageRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ApplicationEventPublisher events;
    private final SessionErrorNotifier errorNotifier;

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
        if (payload.isFailed()) {
            applyFeedbackFailed(session, payload);
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
            breakdownToJson(payload.panelBreakdown()),
            keywordsToJson(payload.studyPlan()),
            keywordsToJson(payload.highlights()),
            payload.reportS3Key()
        );
        // 동시 콜백 경합의 unique 위반은 잡지 않는다 — IDENTITY 전략이라 save 가 즉시 INSERT 하고
        // 실패 시 트랜잭션이 이미 rollback-only 라, catch 후 markProcessed 는 어차피 폐기되고
        // 커밋에서 UnexpectedRollbackException 이 난다. 예외를 그대로 전파시키면 리스너 재시도가
        // 위의 existsBySession_Id 체크에서 멱등 skip 으로 수렴한다.
        feedback = feedbackRepository.save(feedback);

        applyAnswerCoaching(sessionId, payload.answerCoaching());

        // 이전 시도가 실패로 마킹돼 있었다면(재생성 성공·지연 도착 성공) 마커를 걷는다.
        session.clearFeedbackFailure();

        events.publishEvent(RealtimeNotifyEvent.session(sessionId, SseEventType.FEEDBACK_READY,
            new SessionFeedbackNotice(sessionId, feedback.getId())));
        events.publishEvent(RealtimeNotifyEvent.user(session.getUser().getId(), SseEventType.FEEDBACK_READY,
            new SessionFeedbackNotice(sessionId, feedback.getId())));

        markProcessed(envelope.messageId());
        log.info("callback.feedback processed. sessionId={}, feedbackId={}", sessionId, feedback.getId());
    }

    // 답변별 복기를 각 INTERVIEWEE 메시지에 기록. 메시지가 다른 세션이면 방어적으로 skip.
    private void applyAnswerCoaching(Long sessionId, java.util.List<AnswerCoachingItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (AnswerCoachingItem item : items) {
            if (item == null || item.messageId() == null) {
                continue;
            }
            InterviewMessage message = messageRepository.findById(item.messageId()).orElse(null);
            // 답변(INTERVIEWEE) 메시지에만 기록 — 잘못된 messageId 로 질문에 복기가 달리는 것 방지.
            if (message == null
                || !sessionId.equals(message.getSession().getId())
                || message.getRole() != MessageRole.INTERVIEWEE) {
                continue;
            }
            message.recordCoaching(item.modelAnswer(), item.answerRewrite(), item.coachingComment());
            messageRepository.save(message);
        }
    }

    public record SessionFeedbackNotice(Long sessionId, Long feedbackId) {
    }

    // AI 의 예상 못 한 예외로 피드백 생성 자체가 실패한 콜백. 저장할 결과가 없으므로 SSE ERROR 로만
    // 알린다 — 콜백 없이 DLQ 로만 가던 시절의 "피드백 생성 중 무기한 대기"를 끊는 게 목적.
    // errorMessage 는 str(exc) 원문(LLM 게이트웨이 주소·쿼터 상세 등)이라 서버 로그에만 남기고,
    // 클라이언트에는 화이트리스트 코드로 만든 안내 문구만 보낸다(QuestionsCallbackService 와 동일 원칙).
    private void applyFeedbackFailed(InterviewSession session, FeedbackCallbackPayload payload) {
        log.warn("callback.feedback generation failed. sessionId={}, errorCode={}, retriable={}, message={}",
            session.getId(), payload.errorCode(), payload.retriable(), payload.errorMessage());
        // 영속 마커(V29) — SSE ERROR 는 휘발성이라, 그 순간 미접속 클라이언트도 GET 피드백에서
        // 실패를 구분할 수 있게 남긴다 (dirty checking 으로 UPDATE).
        session.markFeedbackFailed(payload.retriable());
        errorNotifier.notify(session.getId(), session.getUser().getId(), new SessionErrorNotice(
            session.getId(), "FEEDBACK", FEEDBACK_FAILED_CODE, FEEDBACK_FAILED_MESSAGE, payload.retriable()));
    }

    // REST(GET 피드백)와 같은 코드·문구 — 채널에 따라 다른 안내가 나가지 않게 단일 출처로 묶는다.
    private static final String FEEDBACK_FAILED_CODE = ApiErrorCode.FEEDBACK_GENERATION_FAILED.name();
    private static final String FEEDBACK_FAILED_MESSAGE =
        ApiErrorCode.FEEDBACK_GENERATION_FAILED.getDefaultMessage();

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

    private String breakdownToJson(
            java.util.List<com.stackup.stackup.session.application.dto.PanelBreakdownItem> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(breakdown);
        } catch (JsonProcessingException e) {
            log.warn("panel_breakdown json serialize failed", e);
            return null;
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
