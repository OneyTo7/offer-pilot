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

    /**
     * Send a chat request using a user-provided API key instead of the platform default.
     * Makes a direct HTTP call to the DeepSeek (OpenAI-compatible) API.
     * When apiKey is null or blank, falls back to {@link #chat(String, String)}.
     */
    String chat(String systemPrompt, String userPrompt, String apiKey);

    /**
     * Send a streaming chat request using a user-provided API key.
     * When apiKey is null or blank, falls back to {@link #chatStream(String, String)}.
     */
    Flux<String> chatStream(String systemPrompt, String userPrompt, String apiKey);
}