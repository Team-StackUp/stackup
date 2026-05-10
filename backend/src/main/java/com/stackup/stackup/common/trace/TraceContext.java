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
}
