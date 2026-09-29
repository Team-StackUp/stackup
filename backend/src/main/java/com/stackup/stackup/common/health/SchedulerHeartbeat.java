package com.stackup.stackup.common.health;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 스케줄러가 살아 있는지 알려 주는 최소 신호.
 *
 * <p>왜 필요한가 — 이 서비스의 스위퍼들은 <b>할 일이 없으면 아무 로그도 남기지 않는다.</b>
 * 그래서 운영 로그만 봐서는 "돌았는데 대상이 없었다"와 "아예 안 돌았다"를 구분할 수 없다.
 * 실제로 운영 백엔드 전체 로그에 sweeper 관련 줄이 <b>0건</b>이다. 그런데 그중 셋은
 * 멈춘 면접을 푸는 유일한 장치라, 스케줄러가 조용히 죽으면 사용자만 알게 된다.
 *
 * <p>하는 일은 타임스탬프 하나를 갱신하는 것뿐이다. 이 작업이 멈췄다면 스케줄러 자체가
 * 멈췄다는 뜻이고({@link SchedulerHealthIndicator} 가 DOWN 으로 보고한다), 다른 스위퍼들도
 * 같이 멈춰 있다고 봐야 한다.
 *
 * <p>DB·네트워크를 건드리지 않는다 — 심장박동이 자기가 감시하는 문제(느린 I/O)로
 * 막히면 안 된다.
 */
@Component
public class SchedulerHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(SchedulerHeartbeat.class);

    // 기동 시각으로 채워 둔다. 비워 두면 **부팅 직후가 "스케줄러가 죽었다"로 보고된다** —
    // Tomcat 이 뜬 뒤 첫 박동(initialDelay 10초)까지 10초 동안 엔드포인트는 정상 응답하면서
    // scheduler=DOWN 을 돌려준다. 2026-09-29 배포에서 실제로 그 창에 5분 주기 감시가
    // 꽂혀 팀 채널에 "[실패] 헬스 이상: DEGRADED" 가 나갔다(12:09:53 기동 / 12:10:00 폴링).
    // 배포마다 헛울리는 알림은 읽히지 않게 되고, 그러면 진짜 장애도 같이 묻힌다.
    //
    // 스케줄러가 영영 안 뛰는 경우도 여전히 잡힌다 — 이 값이 그대로 늙어 maxAge(5분)를
    // 넘으면 DOWN 이다. 감지가 최대 5분 늦어질 뿐이고, 그건 "안 뛴다"의 정의와 같다.
    private final AtomicReference<Instant> lastBeat = new AtomicReference<>(Instant.now());

    @Scheduled(
        fixedDelayString = "${scheduling.heartbeat-interval-ms:60000}",
        initialDelayString = "${scheduling.heartbeat-initial-delay-ms:10000}")
    public void beat() {
        Instant now = Instant.now();
        Instant previous = lastBeat.getAndSet(now);
        if (previous != null && log.isDebugEnabled()) {
            log.debug("scheduler heartbeat. gapMs={}", now.toEpochMilli() - previous.toEpochMilli());
        }
    }

    /** 마지막 박동. 기동 시각으로 시작하므로 null 이 아니다(위 주석 참고). */
    public Instant lastBeat() {
        return lastBeat.get();
    }
}
