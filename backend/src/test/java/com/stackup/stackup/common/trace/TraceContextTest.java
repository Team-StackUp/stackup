package com.stackup.stackup.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * 엔벨로프는 처음부터 traceId 를 실어 왔지만 컨슈머가 MDC 로 되돌리지 않아,
 * 큐를 한 번 건널 때마다 추적이 끊겼다(발행 시점에 새 traceId 가 생성됐다).
 */
class TraceContextTest {

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void runWithTraceId_작업_중에는_MDC에_있고_끝나면_지워진다() {
        List<String> seen = new ArrayList<>();

        TraceContext.runWithTraceId("trace-from-envelope", () -> seen.add(TraceContext.getTraceId()));

        assertThat(seen).containsExactly("trace-from-envelope");
        // 스레드 풀이 재사용되므로 남기면 다음 메시지 로그에 엉뚱한 traceId 가 묻는다.
        assertThat(TraceContext.getTraceId()).isNull();
    }

    @Test
    void runWithTraceId_예외가_나도_MDC를_지운다() {
        try {
            TraceContext.runWithTraceId("t", () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // 컨슈머는 예외를 다시 던져 DLQ 로 보낸다 — 그 경로에서도 정리돼야 한다.
        }

        assertThat(TraceContext.getTraceId()).isNull();
    }

    @Test
    void runWithTraceId_예외를_그대로_전파한다() {
        // 삼키면 메시지가 ack 되어 DLQ 로 가지 않는다.
        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> TraceContext.runWithTraceId("t", () -> {
                throw new IllegalStateException("boom");
            }))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runWithTraceId_비어있으면_새로_만든다() {
        List<String> seen = new ArrayList<>();

        TraceContext.runWithTraceId(null, () -> seen.add(TraceContext.getTraceId()));
        TraceContext.runWithTraceId("  ", () -> seen.add(TraceContext.getTraceId()));

        // 없는 것보다 낫다 — 적어도 그 처리 한 건은 묶인다.
        assertThat(seen).hasSize(2).doesNotContainNull();
        assertThat(seen.get(0)).isNotEqualTo(seen.get(1));
    }
}
