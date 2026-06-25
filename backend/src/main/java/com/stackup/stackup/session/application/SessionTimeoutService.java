package com.stackup.stackup.session.application;

import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.event.SessionEndedEvent;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 시간 초과된 진행 중 세션을 자동 종료한다. 답변이 하나라도 있으면 COMPLETED(→ SessionEndedEvent → 피드백 생성),
// 없으면(자기소개도 안 한 채 방치) INTERRUPTED 로 정리해 피드백을 만들지 않는다.
@Service
@RequiredArgsConstructor
public class SessionTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(SessionTimeoutService.class);
    private static final String REASON = "DURATION_EXCEEDED";

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void endTimedOut(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isDeleted()
            || session.getStatus() != SessionStatus.IN_PROGRESS) {
            return;  // 이미 종료됐거나 레이스 — skip
        }
        boolean hasAnswer =
            messageRepository.existsBySession_IdAndRole(sessionId, MessageRole.INTERVIEWEE);
        Long userId = session.getUser().getId();
        try {
            if (hasAnswer) {
                session.end();  // COMPLETED → SessionEndedEvent 로 피드백 생성
                publishState(session, userId);
                events.publishEvent(new SessionEndedEvent(userId, sessionId, REASON));
                log.info("session auto-completed (timeout). sessionId={}", sessionId);
            } else {
                session.interrupt();  // 답변 없음 → 피드백 없이 정리
                publishState(session, userId);
                log.info("session auto-interrupted (timeout, no answers). sessionId={}", sessionId);
            }
        } catch (IllegalStateException e) {
            log.warn("auto-end skipped — session not IN_PROGRESS. sessionId={}, status={}",
                sessionId, session.getStatus());
        }
    }

    private void publishState(InterviewSession session, Long userId) {
        SessionStateNotice notice =
            new SessionStateNotice(session.getId(), session.getStatus().name(), REASON);
        events.publishEvent(RealtimeNotifyEvent.session(
            session.getId(), SseEventType.SESSION_STATE, notice));
        events.publishEvent(RealtimeNotifyEvent.user(
            userId, SseEventType.SESSION_STATE, notice));
    }

    public record SessionStateNotice(Long sessionId, String status, String reason) {
    }
}
