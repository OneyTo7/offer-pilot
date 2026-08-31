package com.eyki.offerpilot.aicore.advisor;

import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.model.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Advisor that checks for sensitive content in user input.
 * Blocks requests containing harmful/inappropriate content before they reach the LLM.
 */
public class SafeValidAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SafeValidAdvisor.class);

    private static final List<String> SENSITIVE_PATTERNS = List.of();

    private static final int ORDER = 0;

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String userContent = extractUserContent(request);
        if (userContent != null) {
            validateContent(userContent);
        }
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String userContent = extractUserContent(request);
        if (userContent != null) {
            validateContent(userContent);
        }
        return chain.nextStream(request);
    }

    private void validateContent(String content) {
        String lower = content.toLowerCase();
        for (String pattern : SENSITIVE_PATTERNS) {
            if (lower.contains(pattern)) {
                log.warn("检测到敏感内容: pattern={}", pattern);
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "输入内容包含敏感信息，请修改后重试");
            }
        }
    }

    private String extractUserContent(ChatClientRequest request) {
        return request.prompt().getContents();
    }
}