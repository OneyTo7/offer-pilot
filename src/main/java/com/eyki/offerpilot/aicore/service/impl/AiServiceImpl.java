package com.eyki.offerpilot.aicore.service.impl;

import com.eyki.offerpilot.aicore.service.AiService;
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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiServiceImpl(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            String response = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();

            if (response == null) {
                throw BusinessException.aiServiceError("AI 服务返回为空");
            }

            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 服务调用异常", e);
            throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, Map<String, Object> context) {
        try {
            String response = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();

            if (response == null) {
                throw BusinessException.aiServiceError("AI 服务返回为空");
            }

            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 服务调用异常", e);
            throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt) {
        try {
            return chatClient.prompt().system(systemPrompt).user(userPrompt).stream().content().onErrorResume(e -> {
                log.error("AI 流式服务调用异常", e);
                return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
            });
        } catch (Exception e) {
            log.error("AI 流式服务调用异常", e);
            return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt, Map<String, Object> context) {
        try {
            return chatClient.prompt().system(systemPrompt).user(userPrompt).stream().content().onErrorResume(e -> {
                log.error("AI 流式服务调用异常", e);
                return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
            });
        } catch (Exception e) {
            log.error("AI 流式服务调用异常", e);
            return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
        }
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return chat(systemPrompt, userPrompt);
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

            String content = extractContentFromResponse(response.body());
            if (content == null) {
                throw BusinessException.aiServiceError("AI 服务返回内容为空");
            }
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 服务调用异常 (用户 API Key)", e);
            throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return chatStream(systemPrompt, userPrompt);
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

                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                break;
                            }
                            String content = extractContentFromChunk(data);
                            if (content != null && !content.isEmpty()) {
                                emitter.next(content);
                            }
                        }
                    }
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("AI 流式服务调用异常 (用户 API Key)", e);
                emitter.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
            }
        });
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
     * Extract the assistant's response content from a non-streaming API response.
     */
    private String extractContentFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
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
}