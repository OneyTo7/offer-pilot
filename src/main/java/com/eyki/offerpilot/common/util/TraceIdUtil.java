package com.eyki.offerpilot.common.util;

import java.util.UUID;
import org.slf4j.MDC;

public class TraceIdUtil {

    private static final String TRACE_ID_KEY = "traceId";

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId != null ? traceId : "";
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}