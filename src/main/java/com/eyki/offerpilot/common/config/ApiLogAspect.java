package com.eyki.offerpilot.common.config;

import com.eyki.offerpilot.common.util.TraceIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Aspect that logs all REST controller requests with timing.
 */
@Slf4j
@Aspect
@Component
public class ApiLogAspect {

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        // Get request info
        ServletRequestAttributes attributes = (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
        String method = "UNKNOWN";
        String uri = "UNKNOWN";
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            method = request.getMethod();
            uri = request.getRequestURI();
            String query = request.getQueryString();
            if (query != null) {
                uri += "?" + query;
            }
        }

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String args = Arrays.toString(joinPoint.getArgs());
        String traceId = TraceIdUtil.getTraceId();

        Instant start = Instant.now();
        log.info("[API 请求] traceId={}, {}{} {}.{}(), args={}", traceId, method, uri, className, methodName,
            truncateArgs(args));

        try {
            Object result = joinPoint.proceed();
            Duration elapsed = Duration.between(start, Instant.now());
            log.info("[API 响应] traceId={}, {}{} 耗时={}ms", traceId, method, uri, elapsed.toMillis());
            return result;
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            log.error("[API 异常] traceId={}, {}{} 耗时={}ms, error={}", traceId, method, uri, elapsed.toMillis(),
                e.getMessage());
            throw e;
        }
    }

    private String truncateArgs(String args) {
        if (args == null) {
            return "null";
        }
        return args.length() > 500 ? args.substring(0, 500) + "...(" + args.length() + " chars)" : args;
    }
}