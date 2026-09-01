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
     * Send a chat request using a user-provided API key, with advisor context.
     * <p>
     * When apiKey is null or blank, uses the ChatClient path so the context is passed
     * to the advisor chain (chat memory, RAG filter, etc.).
     * When apiKey is provided, makes a direct HTTP call (advisors do not apply).
     *
     * @param context advisor context params, e.g. {@code "vector_store_filter_expression"},
     *                {@code "conversation_id"}
     */
    String chat(String systemPrompt, String userPrompt, String apiKey, Map<String, Object> context);

    /**
     * Send a streaming chat request using a user-provided API key.
     * When apiKey is null or blank, falls back to {@link #chatStream(String, String)}.
     */
    Flux<String> chatStream(String systemPrompt, String userPrompt, String apiKey);

    /**
     * Send a streaming chat request using a user-provided API key, with advisor context.
     * <p>
     * When apiKey is null or blank, uses the ChatClient path so the context is passed
     * to the advisor chain (chat memory, RAG filter, etc.).
     * When apiKey is provided, makes a direct HTTP call (advisors do not apply).
     *
     * @param context advisor context params, e.g. {@code "vector_store_filter_expression"},
     *                {@code "conversation_id"}
     */
    Flux<String> chatStream(String systemPrompt, String userPrompt, String apiKey, Map<String, Object> context);

    /**
     * Send a chat request and get a structured entity response.
     * <p>
     * Internally uses {@code ChatClient.call().entity()} when using the platform key,
     * or {@code BeanOutputConverter} when a user-provided API key is supplied.
     * Both paths auto-generate the JSON Schema from the entity class, append it to the
     * system prompt as a format constraint, and parse the response into the target type.
     *
     * @param <T>          the entity type
     * @param systemPrompt the system prompt
     * @param userPrompt   the user prompt
     * @param apiKey       user's DeepSeek API key (null/blank → platform key)
     * @param entityClass  the target entity class
     * @return parsed entity
     */
    <T> T chatWithEntity(String systemPrompt, String userPrompt, String apiKey, Class<T> entityClass);

    /**
     * Send a chat request and get a structured entity response, with advisor context.
     * <p>
     * The context map is passed to the advisor chain, enabling features like:
     * <ul>
     *   <li>{@code "conversation_id"} — chat memory injection via MessageChatMemoryAdvisor</li>
     *   <li>{@code "vector_store_filter_expression"} — user-level RAG isolation via RetrievalAugmentationAdvisor</li>
     * </ul>
     *
     * @param <T>          the entity type
     * @param systemPrompt the system prompt
     * @param userPrompt   the user prompt
     * @param apiKey       user's DeepSeek API key (null/blank → platform key)
     * @param entityClass  the target entity class
     * @param context      advisor context params (passed to AdvisorSpec)
     * @return parsed entity
     */
    <T> T chatWithEntity(String systemPrompt, String userPrompt, String apiKey, Class<T> entityClass, Map<String, Object> context);

    /**
     * Structured output without RAG augmentation — for pure extraction scenarios
     * (e.g. resume parsing) where knowledge-base injection would pollute the result.
     * Uses a dedicated ChatClient whose advisor chain excludes RetrievalAugmentationAdvisor.
     */
    <T> T chatWithEntityNoRag(String systemPrompt, String userPrompt, Class<T> entityClass);
}