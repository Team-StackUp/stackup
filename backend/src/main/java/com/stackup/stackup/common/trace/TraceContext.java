package com.stackup.stackup.common.trace;

import org.slf4j.MDC;

public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";

    private TraceContext() {
    }

    public static String getTraceId() {
        return MDC.get(MDC_TRACE_ID);
    }

    public static void setTraceId(String traceId) {
        MDC.put(MDC_TRACE_ID, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_TRACE_ID);
    }

    /**
     * 비동기 처리(RabbitMQ 컨슈머)를 원 요청의 traceId 아래에서 실행한다.
     *
     * <p>엔벨로프는 처음부터 traceId 를 실어 왔지만 <b>컨슈머가 그걸 MDC 로 되돌리지
     * 않았다.</b> 그래서 콜백 처리 로그는 traceId 없이 남고, 이어서 발행하는 메시지는
     * {@code RabbitMessagePublisher} 가 <b>새 traceId 를 만들어</b> 붙였다(그 코드의 주석도
     * "RabbitListener / EventListener 등에서 MDC 미주입 흐름" 이라고 적고 있다).
     * 결과적으로 큐를 한 번 건널 때마다 추적이 끊겨, "한 요청의 모든 후속 처리에 동일
     * traceId 전파"(docs/observability.md §1)가 실제로는 성립하지 않았다.
     *
     * <p>traceId 가 비어 있으면 새로 만든다 — 없는 것보다 낫고, 적어도 그 처리 한 건은
     * 묶인다. 끝나면 반드시 지운다(스레드 풀 재사용 시 남은 값이 다음 메시지에 묻는다).
     */
    public static void runWithTraceId(String traceId, Runnable work) {
        String effective = (traceId == null || traceId.isBlank())
            ? java.util.UUID.randomUUID().toString()
            : traceId;
        try {
            setTraceId(effective);
            work.run();
        } finally {
            clear();
        }
    }
}
