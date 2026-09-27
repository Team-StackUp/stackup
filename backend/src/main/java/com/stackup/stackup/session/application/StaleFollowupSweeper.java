package com.stackup.stackup.session.application;

import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 꼬리질문 콜백을 영영 못 받은 placeholder 를 정리한다.
 *
 * <p>스트리밍 꼬리질문은 `(생성 중)` placeholder 를 먼저 INSERT 하고
 * `callback.questions(FOLLOWUP)` 이 도착해야 확정된다. AI 가 <b>실패를 보고</b>하면
 * {@code QuestionsCallbackService.applyFollowupFailed} 가 실패 문구로 확정하고 다음
 * 일반질문으로 넘어가지만, <b>콜백 자체가 오지 않으면</b>(AI 크래시·DLQ 격리·브로커 단절)
 * 그 경로를 아무도 타지 못한다. placeholder 는 CREATED 인 채로 남고, 화면에는 "(생성 중)"
 * 만 떠 있으며, 답할 질문이 없으니 면접이 그 자리에서 멈춘다 — 세션 시간 초과로 통째로
 * 끝날 때까지.
 *
 * <p>음성 답변의 `(transcribing)` 이 정확히 같은 구멍이었고 {@link StaleTranscriptionSweeper}
 * 로 막았다. 이건 그 짝이다. 운영에서 1건 발생했다(messageId=444, sessionId=93 —
 * 28일째 CREATED 로 남아 있었고 그 세션은 시간초과로 조기 종료됐다).
 */
@Component
@RequiredArgsConstructor
public class StaleFollowupSweeper {

    private static final Logger log = LoggerFactory.getLogger(StaleFollowupSweeper.class);

    private final InterviewMessageRepository messageRepository;
    private final QuestionsCallbackService callbackService;

    // 이 시간이 지나도 확정되지 않으면 유실로 본다. 정상 생성은 flash 타임아웃(10초) + RAG(1.5초)
    // 범위에서 끝나고, AI 쪽에 메시지 단위 재시도가 없어 실패는 곧장 DLQ 로 간다 —
    // 3분이면 정상 케이스를 자를 위험 없이 넉넉하다.
    @Value("${interview.followup.stale-generation-minutes:3}")
    private long staleAfterMinutes = 3;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Scheduled(
        fixedDelayString = "${interview.followup.sweep-interval-ms:120000}",
        initialDelayString = "${interview.followup.sweep-initial-delay-ms:90000}")
    public void sweep() {
        Instant before = Instant.now().minus(Duration.ofMinutes(staleAfterMinutes));
        List<InterviewMessage> stale = messageRepository.findStaleFollowupPlaceholders(
            InterviewMessage.FOLLOWUP_GENERATING_TEXT, before);
        if (stale.isEmpty()) {
            return;
        }
        int recovered = 0;
        for (InterviewMessage m : stale) {
            try {
                // 메시지마다 독립 트랜잭션 — 하나가 실패해도 나머지는 정리된다.
                callbackService.failStaleFollowup(m.getId());
                recovered++;
            } catch (RuntimeException e) {
                log.warn("stale follow-up recovery failed. messageId={}", m.getId(), e);
            }
        }
        log.info("stale follow-up sweeper recovered {} of {} stuck placeholder(s)",
            recovered, stale.size());
    }
}
