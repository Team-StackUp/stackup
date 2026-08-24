package com.stackup.stackup.log.ai.application;

import com.stackup.stackup.log.ai.domain.AiRequestLogRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보존 기한이 지난 AI 호출 로그를 정리한다.
 *
 * <p>LLM 호출마다 한 행이 쌓인다 — 피드백 한 번에 10여 건(패널·첫인상·직무적합·인성·답변별
 * 코칭)에 TTS 는 문장마다다. <b>이 코드베이스에서 가장 빨리 자라는 테이블</b>인데 지우는
 * 쪽이 없었다(#224 의 processed_messages 와 같은 종류).
 *
 * <p>보존 기간을 넉넉히(기본 90일) 잡은 이유: 이 테이블의 가치는 <b>비용 추이</b>다.
 * 멱등 레코드와 달리 지운다고 동작이 깨지지는 않지만, 짧게 잡으면 "지난 학기 대비
 * 토큰이 얼마나 늘었나" 같은 질문에 답할 수 없게 된다. 한 학기를 덮는 값으로 시작하고
 * 필요하면 환경변수로 조절한다.
 */
@Component
@RequiredArgsConstructor
public class AiRequestLogSweeper {

    private static final Logger log = LoggerFactory.getLogger(AiRequestLogSweeper.class);

    private final AiRequestLogRepository logRepository;

    @Value("${observability.ai-log-retention-days:90}")
    private long retentionDays = 90;

    @Transactional
    @Scheduled(
        fixedDelayString = "${observability.ai-log-sweep-interval-ms:86400000}",
        initialDelayString = "${observability.ai-log-sweep-initial-delay-ms:600000}")
    public void sweep() {
        int deleted = logRepository.deleteCreatedBefore(
            Instant.now().minus(Duration.ofDays(retentionDays)));
        if (deleted > 0) {
            log.info("ai request logs swept. deleted={}, retentionDays={}", deleted, retentionDays);
        }
    }
}
