package com.stackup.stackup.session.application;

import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 주기적으로 시간 초과된 진행 중 세션을 자동 종료한다(maxDurationMinutes 자동종료 — 좀비 세션 방지).
// 종료 한도는 세션별 maxDurationMinutes(startedAt 기준). 진행 세션은 소수라 전체 조회 후 메모리 필터로 충분.
// 멀티 인스턴스에서 중복 실행돼도 endTimedOut 이 IN_PROGRESS 재확인 + 피드백 멱등으로 안전하다.
@Component
@RequiredArgsConstructor
public class SessionTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(SessionTimeoutSweeper.class);

    private final InterviewSessionRepository sessionRepository;
    private final SessionTimeoutService timeoutService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Scheduled(
        fixedDelayString = "${interview.session.sweep-interval-ms:300000}",
        initialDelayString = "${interview.session.sweep-initial-delay-ms:60000}")
    public void sweep() {
        Instant now = Instant.now();
        List<InterviewSession> inProgress =
            sessionRepository.findByStatusAndDeletedFalse(SessionStatus.IN_PROGRESS);
        int ended = 0;
        for (InterviewSession s : inProgress) {
            if (!isTimedOut(s, now)) {
                continue;
            }
            try {
                timeoutService.endTimedOut(s.getId());  // 세션마다 독립 트랜잭션
                ended++;
            } catch (RuntimeException e) {
                log.warn("session timeout-end failed. sessionId={}", s.getId(), e);
            }
        }
        if (ended > 0) {
            log.info("session sweeper ended {} timed-out session(s) of {} in-progress",
                ended, inProgress.size());
        }
    }

    // 기준 시각은 startedAt 이 아니라 durationAnchor() — 이어하기로 재개했다면 그 시각부터
    // 다시 잰다. 아니면 재개하자마자 스위퍼가 즉시 다시 중단시킨다.
    private boolean isTimedOut(InterviewSession s, Instant now) {
        Instant anchor = s.durationAnchor();
        if (anchor == null || s.getMaxDurationMinutes() == null) {
            return false;
        }
        return now.isAfter(anchor.plus(s.getMaxDurationMinutes(), ChronoUnit.MINUTES));
    }
}
