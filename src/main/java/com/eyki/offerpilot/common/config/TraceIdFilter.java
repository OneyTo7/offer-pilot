package com.eyki.offerpilot.common.config;

import com.eyki.offerpilot.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String traceId = TraceIdUtil.generateTraceId();
            TraceIdUtil.setTraceId(traceId);
            response.setHeader("X-Trace-Id", traceId);
            filterChain.doFilter(request, response);
        } finally {
            TraceIdUtil.clear();
        }
    }
}