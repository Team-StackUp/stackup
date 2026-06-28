package com.stackup.stackup.session.application;

import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.event.SessionEndedEvent;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionStatus;
import java.time.Instant;
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
        SessionStatus target = hasAnswer ? SessionStatus.COMPLETED : SessionStatus.INTERRUPTED;

        // 원자적 종료 전이: IN_PROGRESS 일 때만 1행이 갱신된다. 0이면 다른 트랜잭션
        // (다른 스위퍼 인스턴스·수동 종료·콜백 종료)이 먼저 종료한 것 → 부수효과 미발행.
        int claimed = sessionRepository.finishIfInProgress(sessionId, target, Instant.now());
        if (claimed == 0) {
            log.info("session auto-end skipped — already ended by another tx. sessionId={}", sessionId);
            return;
        }

        publishState(sessionId, userId, target);
        if (target == SessionStatus.COMPLETED) {
            // COMPLETED → SessionEndedEvent 로 피드백 생성. 전이를 차지한 트랜잭션에서 단 한 번만.
            events.publishEvent(new SessionEndedEvent(userId, sessionId, REASON));
            log.info("session auto-completed (timeout). sessionId={}", sessionId);
        } else {
            log.info("session auto-interrupted (timeout, no answers). sessionId={}", sessionId);
        }
    }

    private void publishState(Long sessionId, Long userId, SessionStatus status) {
        SessionStateNotice notice = new SessionStateNotice(sessionId, status.name(), REASON);
        events.publishEvent(RealtimeNotifyEvent.session(
            sessionId, SseEventType.SESSION_STATE, notice));
        events.publishEvent(RealtimeNotifyEvent.user(
            userId, SseEventType.SESSION_STATE, notice));
    }

    public record SessionStateNotice(Long sessionId, String status, String reason) {
    }
}
