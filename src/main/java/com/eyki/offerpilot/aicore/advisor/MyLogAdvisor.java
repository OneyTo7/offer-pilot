package com.eyki.offerpilot.aicore.advisor;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * Advisor that logs AI request/response details including timing.
 */
public class MyLogAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(MyLogAdvisor.class);

    private static final int ORDER = 4;

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
        Instant start = Instant.now();
        String promptPreview = truncate(request.prompt().getContents(), 200);

        log.info("AI 请求: prompt={}", promptPreview);
        ChatClientResponse response = chain.nextCall(request);
        Duration elapsed = Duration.between(start, Instant.now());

        log.info("AI 响应完成: 耗时={}ms", elapsed.toMillis());
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Instant start = Instant.now();
        String promptPreview = truncate(request.prompt().getContents(), 200);

        log.info("AI 流式请求: prompt={}", promptPreview);
        Flux<ChatClientResponse> responses = chain.nextStream(request);

        return responses.doOnComplete(() -> {
            Duration elapsed = Duration.between(start, Instant.now());
            log.info("AI 流式响应完成: 耗时={}ms", elapsed.toMillis());
        }).doOnError(error -> {
            Duration elapsed = Duration.between(start, Instant.now());
            log.error("AI 流式响应异常: 耗时={}ms, error={}", elapsed.toMillis(), error.getMessage());
        });
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "null";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}