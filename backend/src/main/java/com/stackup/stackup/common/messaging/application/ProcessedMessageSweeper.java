package com.stackup.stackup.common.messaging.application;

import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
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
 * 보존 기한이 지난 멱등 레코드를 정리한다.
 *
 * <p>이 프로젝트는 Redis 를 쓰지 않고 "휘발성 데이터는 PostgreSQL 의 short-lived 레코드"로
 * 처리하기로 했다(루트 CLAUDE.md). 그런데 아래 둘은 <b>지우는 쪽이 없어 사실상 영구 보관</b>
 * 이었다 — short-lived 라는 전제가 코드로는 지켜지지 않았다.
 *
 * <p>processed_messages 는 AI 콜백마다 한 행씩 쌓인다(질문·꼬리질문·피드백·분석·음성·TTS).
 * {@code idx_processed_messages_processed_at} 인덱스가 처음부터 있는데 그 컬럼으로 조회하는
 * 코드가 하나도 없었다 — 보존 정리를 전제로 만든 인덱스인데 정작 정리가 없었던 셈이다.
 *
 * <p>멱등 레코드의 보존 기간은 <b>재전달 창보다 길어야</b> 한다. 너무 일찍 지우면 DLQ 에서
 * 늦게 재주입된 메시지가 "처음 보는 메시지"가 되어 다시 처리된다(질문이 두 번 붙거나
 * 피드백이 다시 생성될 수 있다). 기본 30일은 그 창보다 충분히 길다.
 *
 * <p>oauth_states 는 여기서 다루지 않는다 — 발급할 때마다 만료분을 지워 스스로 자정한다
 * ({@code OAuthStateService.issueStateWithPkce}).
 */
@Component
@RequiredArgsConstructor
public class ProcessedMessageSweeper {

    private static final Logger log = LoggerFactory.getLogger(ProcessedMessageSweeper.class);

    private final ProcessedMessageRepository processedMessageRepository;

    @Value("${messaging.processed-message-retention-days:30}")
    private long processedRetentionDays = 30;

    @Transactional
    @Scheduled(
        fixedDelayString = "${messaging.volatile-sweep-interval-ms:86400000}",
        initialDelayString = "${messaging.volatile-sweep-initial-delay-ms:300000}")
    public void sweep() {
        Instant now = Instant.now();
        int processed = processedMessageRepository.deleteProcessedBefore(
            now.minus(Duration.ofDays(processedRetentionDays)));
        if (processed > 0) {
            log.info("processed message sweep done. deleted={}, retentionDays={}",
                processed, processedRetentionDays);
        }
    }
}
