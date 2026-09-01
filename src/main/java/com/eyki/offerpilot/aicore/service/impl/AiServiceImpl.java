package com.eyki.offerpilot.aicore.service.impl;

import com.eyki.offerpilot.aicore.service.AiService;
import com.eyki.offerpilot.aicore.usage.service.UserTokenUsageService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI service implementation. Wraps Spring AI's ChatClient to provide synchronous
 * and streaming LLM invocations. Uses DeepSeek via OpenAI-compatible API.
 *
 * <p>When a user provides their own DeepSeek API key, the service makes a direct
 * HTTP call to the OpenAI-compatible API endpoint, bypassing the configured ChatClient.
 * This enables per-user API key isolation and unlimited usage for pro-tier users.
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String DEEPSEEK_MODEL = "deepseek-chat";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    /** Context key for passing userId to enable token usage recording. */
    private static final String CTX_USER_ID = "user_id";

    private final ChatClient chatClient;
    private final ChatClient chatClientNoRag;
    private final ObjectMapper objectMapper;
    private final UserTokenUsageService tokenUsageService;

    public AiServiceImpl(ChatClient chatClient, ChatClient chatClientNoRag, ObjectMapper objectMapper,
        UserTokenUsageService tokenUsageService) {
        this.chatClient = chatClient;
        this.chatClientNoRag = chatClientNoRag;
        this.objectMapper = objectMapper;
        this.tokenUsageService = tokenUsageService;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, String apiKey, Map<String, Object> context) {
        if (apiKey == null || apiKey.isBlank()) {
            // Platform key: ChatClient path, advisors apply (RAG, memory, validation, logging)
            try {
                var spec = chatClient.prompt().system(systemPrompt).user(userPrompt);
                if (context != null && !context.isEmpty()) {
                    spec.advisors(advisor -> context.forEach(advisor::param));
                }
                ChatResponse chatResponse = spec.call().chatResponse();
                String response = chatResponse.getResult().getOutput().getText();

                if (response == null) {
                    throw BusinessException.aiServiceError("AI 服务返回为空");
                }

                // Record token usage for platform key
                recordUsage(context, chatResponse);
                return response;
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("AI 服务调用异常", e);
                throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
            }
        }

        try {
            String jsonBody = buildChatRequestBody(systemPrompt, userPrompt, false);

            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEEPSEEK_API_URL))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("AI 服务返回异常状态码: status={}, body={}", response.statusCode(), response.body());
                throw BusinessException.aiServiceError("AI 服务调用失败，状态码: " + response.statusCode());
            }

            // Extract content and usage from response
            JsonNode root = objectMapper.readTree(response.body());
            String content = extractContentFromResponse(root);
            if (content == null) {
                throw BusinessException.aiServiceError("AI 服务返回内容为空");
            }

            // Record token usage for user API key too (for display/stats)
            recordUsage(context, root);
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 服务调用异常 (用户 API Key)", e);
            throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt, String apiKey, Map<String, Object> context) {
        if (apiKey == null || apiKey.isBlank()) {
            // Platform key: ChatClient path, advisors apply (RAG, memory, validation, logging)
            try {
                var spec = chatClient.prompt().system(systemPrompt).user(userPrompt);
                if (context != null && !context.isEmpty()) {
                    spec.advisors(advisor -> context.forEach(advisor::param));
                }
                // Note: Flux<String> from .stream().content() doesn't expose token usage.
                // Token recording for platform key streaming is skipped.
                return spec.stream().content().onErrorResume(e -> {
                    log.error("AI 流式服务调用异常", e);
                    return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
                });
            } catch (Exception e) {
                log.error("AI 流式服务调用异常", e);
                return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
            }
        }

        return Flux.create(emitter -> {
            try {
                String jsonBody = buildChatRequestBody(systemPrompt, userPrompt, true);

                HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEEPSEEK_API_URL))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .timeout(STREAM_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

                HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    log.error("AI 流式服务返回异常状态码: status={}, body={}", response.statusCode(), errorBody);
                    emitter.error(BusinessException.aiServiceError("AI 服务调用失败，状态码: " + response.statusCode()));
                    return;
                }

                // DeepSeek 流式响应的最后一条 data 包含 usage 信息，格式为：
                // data: {"choices":[],"usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150}}
                String lastChunk = null;

                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                break;
                            }
                            // 检查是否包含 usage（最后一条 chunk）
                            if (data.contains("\"usage\"")) {
                                lastChunk = data;
                            } else {
                                String content = extractContentFromChunk(data);
                                if (content != null && !content.isEmpty()) {
                                    emitter.next(content);
                                }
                            }
                        }
                    }
                    emitter.complete();
                }

                // 记录最后一条 chunk 中的 token 用量
                if (lastChunk != null) {
                    recordUsageFromChunk(context, lastChunk);
                }
            } catch (Exception e) {
                log.error("AI 流式服务调用异常 (用户 API Key)", e);
                emitter.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
            }
        });
    }

    @Override
    public <T> T chatWithEntity(String systemPrompt, String userPrompt, String apiKey, Class<T> entityClass,
        Map<String, Object> context) {
        if (apiKey == null || apiKey.isBlank()) {
            // Platform key: use ChatClient.chatResponse() to get content + usage,
            // then parse with BeanOutputConverter
            try {
                var spec = chatClient.prompt().system(systemPrompt).user(userPrompt);
                if (context != null && !context.isEmpty()) {
                    spec.advisors(advisor -> context.forEach(advisor::param));
                }
                ChatResponse chatResponse = spec.call().chatResponse();
                String content = chatResponse.getResult().getOutput().getText();
                if (content == null) {
                    throw BusinessException.aiServiceError("AI 结构化输出返回为空");
                }

                // Record token usage
                recordUsage(context, chatResponse);

                BeanOutputConverter<T> converter = new BeanOutputConverter<>(entityClass);
                return converter.convert(content);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("AI 结构化输出调用异常 (平台 Key)", e);
                throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
            }
        } else {
            // User API key: use BeanOutputConverter manually
            // chat() already records token usage, pass context forward
            try {
                BeanOutputConverter<T> converter = new BeanOutputConverter<>(entityClass);
                String format = converter.getFormat();
                String fullSystemPrompt = systemPrompt + "\n\n" + format;
                String response = chat(fullSystemPrompt, userPrompt, apiKey, context);
                return converter.convert(response);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("AI 结构化输出解析失败 (用户 API Key)", e);
                throw BusinessException.aiServiceError("AI 响应解析失败: " + e.getMessage());
            }
        }
    }

    @Override
    public <T> T chatWithEntityNoRag(String systemPrompt, String userPrompt, Class<T> entityClass) {
        // Uses the dedicated no-RAG ChatClient (SafeValid → ReReading → Log only)
        // Note: no context map passed, so token usage is not recorded for this path.
        try {
            ChatResponse chatResponse = chatClientNoRag.prompt().system(systemPrompt).user(userPrompt).call().chatResponse();
            String content = chatResponse.getResult().getOutput().getText();
            if (content == null) {
                throw BusinessException.aiServiceError("AI 结构化输出返回为空");
            }
            BeanOutputConverter<T> converter = new BeanOutputConverter<>(entityClass);
            return converter.convert(content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 结构化输出调用异常 (无 RAG)", e);
            throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
        }
    }

    // ========== Helper methods ==========

    /**
     * Build the JSON request body for the OpenAI-compatible chat completions API.
     */
    private String buildChatRequestBody(String systemPrompt, String userPrompt, boolean stream) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("model", DEEPSEEK_MODEL);
            root.put("stream", stream);

            var messages = root.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    /**
     * Extract the assistant's response content from a parsed JSON response.
     */
    private String extractContentFromResponse(JsonNode root) {
        try {
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    return Optional.ofNullable(message.get("content"))
                        .map(JsonNode::asText)
                        .orElse(null);
                }
            }
            return null;
        } catch (Exception e) {
            log.error("解析 AI 响应 JSON 失败", e);
            return null;
        }
    }

    /**
     * Extract content delta from a streaming SSE chunk.
     */
    private String extractContentFromChunk(String chunkData) {
        try {
            JsonNode root = objectMapper.readTree(chunkData);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null) {
                    return Optional.ofNullable(delta.get("content"))
                        .map(JsonNode::asText)
                        .orElse(null);
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("解析流式数据块失败: {}", chunkData, e);
            return null;
        }
    }

    // ========== Token usage recording ==========

    /**
     * Extract userId from context map.
     */
    private Long extractUserId(Map<String, Object> context) {
        if (context == null) return null;
        Object userId = context.get(CTX_USER_ID);
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        if (userId instanceof String) {
            try {
                return Long.parseLong((String) userId);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Record token usage from a ChatResponse (Spring AI platform key path).
     */
    private void recordUsage(Map<String, Object> context, ChatResponse chatResponse) {
        Long userId = extractUserId(context);
        if (userId == null) return;
        try {
            var usage = chatResponse.getMetadata().getUsage();
            if (usage != null) {
                int promptTokens = Objects.requireNonNullElse(usage.getPromptTokens(), 0);
                int completionTokens = Objects.requireNonNullElse(usage.getCompletionTokens(), 0);
                tokenUsageService.record(userId, promptTokens, completionTokens);
            }
        } catch (Exception e) {
            log.warn("记录 ChatResponse token 用量失败: userId={}", userId, e);
        }
    }

    /**
     * Record token usage from a raw JSON response (user API key path, direct HTTP).
     */
    private void recordUsage(Map<String, Object> context, JsonNode root) {
        Long userId = extractUserId(context);
        if (userId == null) return;
        try {
            JsonNode usage = root.get("usage");
            if (usage != null) {
                int promptTokens = usage.get("prompt_tokens").asInt(0);
                int completionTokens = usage.get("completion_tokens").asInt(0);
                tokenUsageService.record(userId, promptTokens, completionTokens);
            }
        } catch (Exception e) {
            log.warn("记录 JSON token 用量失败: userId={}", userId, e);
        }
    }

    /**
     * Record token usage from a streaming SSE chunk that contains usage data.
     */
    private void recordUsageFromChunk(Map<String, Object> context, String chunkData) {
        Long userId = extractUserId(context);
        if (userId == null) return;
        try {
            JsonNode root = objectMapper.readTree(chunkData);
            JsonNode usage = root.get("usage");
            if (usage != null) {
                int promptTokens = usage.get("prompt_tokens").asInt(0);
                int completionTokens = usage.get("completion_tokens").asInt(0);
                tokenUsageService.record(userId, promptTokens, completionTokens);
            }
        } catch (Exception e) {
            log.warn("记录流式 chunk token 用量失败: userId={}", userId, e);
        }
    }
}