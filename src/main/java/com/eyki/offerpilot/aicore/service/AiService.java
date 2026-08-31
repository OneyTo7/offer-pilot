package com.eyki.offerpilot.aicore.service;

import java.util.Map;
import reactor.core.publisher.Flux;

public interface AiService {

    /**
     * Send a chat request and get a complete response.
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * Send a chat request with additional context.
     */
    String chat(String systemPrompt, String userPrompt, Map<String, Object> context);

    /**
     * Send a chat request and get a streaming response.
     */
    Flux<String> chatStream(String systemPrompt, String userPrompt);

    /**
     * Send a chat request with streaming response and additional context.
     */
    Flux<String> chatStream(String systemPrompt, String userPrompt, Map<String, Object> context);
}