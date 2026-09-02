package com.eyki.offerpilot.aicore.advisor;

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
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

/**
 * 用户自备 API Key 的路由 Advisor — 把直连 DeepSeek 的逻辑下沉到 advisor 链内，
 * 使用户 key 路径与平台 key 路径共享同一套编排（安全校验、记忆注入、RAG、日志、用量控制）。
 *
 * <p><b>拦截条件</b>：advisor context 含 {@code api_key} 时，本 advisor 不走
 * {@code ChatModelCallAdvisor}（平台 ChatModel 绑定的是平台 key），而是直连
 * DeepSeek OpenAI 兼容接口并构造响应；未传 api_key 时 {@code chain.nextCall()} 透传。</p>
 *
 * <p><b>执行位置</b>：order=5，位于 MyLogAdvisor(4) 之后、ChatModelCallAdvisor(MAX) 之前——
 * 前序 advisor（ReReading、记忆注入、RAG 增强）已把最终 prompt 准备好，此处拿到的
 * {@code request.prompt()} 即增强后的完整消息列表，直连时全部按 OpenAI 格式透传。</p>
 *
 * <p><b>用量</b>：同步响应与流式最后一个 chunk 的 metadata 都带 usage，
 * 供 {@link TokenUsageAdvisor} 后置累计（仅统计展示，直连路径不做前置限额校验）。</p>
 */
public class ApiKeyRoutingAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRoutingAdvisor.class);

    private static final int ORDER = 5;
    private static final String CTX_API_KEY = "api_key";

    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String DEEPSEEK_MODEL = "deepseek-chat";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ApiKeyRoutingAdvisor(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build());
    }

    /** 测试用：可注入 mock HttpClient。 */
    public ApiKeyRoutingAdvisor(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

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
        String apiKey = extractApiKey(request);
        if (apiKey == null) {
            // 平台 key：交给默认 ChatModel
            return chain.nextCall(request);
        }
        // 用户 key：拦截模型调用，直连 DeepSeek
        try {
            String jsonBody = buildRequestBody(request.prompt());
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(DEEPSEEK_API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("AI 服务返回异常状态码: status={}, body={}", response.statusCode(), response.body());
                throw BusinessException.aiServiceError("AI 服务调用失败，状态码: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = extractContentFromResponse(root);
            if (content == null) {
                throw BusinessException.aiServiceError("AI 服务返回内容为空");
            }
            ChatResponse chatResponse = buildChatResponse(content, extractUsage(root));
            return new ChatClientResponse(chatResponse, request.context());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 服务调用异常 (用户 API Key)", e);
            throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String apiKey = extractApiKey(request);
        if (apiKey == null) {
            // 平台 key：交给默认 ChatModel
            return chain.nextStream(request);
        }
        // 用户 key：直连 DeepSeek 流式接口，SSE 逐 chunk 转发
        return Flux.create(emitter -> {
            try {
                String jsonBody = buildRequestBody(request.prompt(), true);
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(DEEPSEEK_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(STREAM_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

                HttpResponse<InputStream> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    log.error("AI 流式服务返回异常状态码: status={}, body={}", response.statusCode(), errorBody);
                    emitter.error(BusinessException.aiServiceError("AI 服务调用失败，状态码: " + response.statusCode()));
                    return;
                }

                // DeepSeek 流式响应的最后一条 data 包含 usage，格式为：
                // data: {"choices":[],"usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150}}
                DefaultUsage lastUsage = null;

                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data: ")) {
                            continue;
                        }
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        // 检查是否包含 usage（最后一条 chunk）
                        if (data.contains("\"usage\"")) {
                            lastUsage = parseUsageFromChunk(data);
                        } else {
                            String content = extractContentFromChunk(data);
                            if (content != null && !content.isEmpty()) {
                                ChatResponse chunkResponse = buildChatResponse(content, null);
                                emitter.next(new ChatClientResponse(chunkResponse, request.context()));
                            }
                        }
                    }
                }

                // 最后补发带 usage 的空内容 chunk，供 TokenUsageAdvisor 后置累计
                if (lastUsage != null) {
                    ChatResponse finalResponse = buildChatResponse("", lastUsage);
                    emitter.next(new ChatClientResponse(finalResponse, request.context()));
                }
                emitter.complete();
            } catch (BusinessException e) {
                emitter.error(e);
            } catch (Exception e) {
                log.error("AI 流式服务调用异常 (用户 API Key)", e);
                emitter.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
            }
        });
    }

    // ========== Helper methods ==========

    /**
     * 从 advisor context 提取用户 API key；未传 api_key 返回 null（透传给平台 ChatModel）。
     */
    private String extractApiKey(ChatClientRequest request) {
        Object apiKey = request.context().get(CTX_API_KEY);
        if (apiKey instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }

    /**
     * 将增强后的 Prompt 消息列表构建为 OpenAI 兼容的 messages 数组。
     * MemoryAdvisor 注入的历史消息（user/assistant 交替）也会完整透传。
     */
    private String buildRequestBody(org.springframework.ai.chat.prompt.Prompt prompt) {
        return buildRequestBody(prompt, false);
    }

    private String buildRequestBody(org.springframework.ai.chat.prompt.Prompt prompt, boolean stream) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("model", DEEPSEEK_MODEL);
            root.put("stream", stream);

            var messages = root.putArray("messages");
            for (Message message : prompt.getInstructions()) {
                String role = switch (message) {
                    case SystemMessage ignored -> "system";
                    case AssistantMessage ignored -> "assistant";
                    case UserMessage ignored -> "user";
                    default -> null; // tool 等其他消息不适用
                };
                if (role == null || message.getText() == null) {
                    continue;
                }
                messages.addObject().put("role", role).put("content", message.getText());
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    /**
     * 构造带 usage 的 ChatResponse（usage 为 null 时 metadata 为空）。
     */
    private ChatResponse buildChatResponse(String content, DefaultUsage usage) {
        List<Generation> generations = List.of(new Generation(new AssistantMessage(content)));
        if (usage == null) {
            return new ChatResponse(generations, ChatResponseMetadata.builder().build());
        }
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
            .usage(usage)
            .build();
        return new ChatResponse(generations, metadata);
    }

    /**
     * 从同步响应 JSON 提取 assistant 文本内容。
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
     * 从同步响应 JSON 提取 usage（无 usage 字段返回 null，TokenUsageAdvisor 将跳过累计）。
     */
    private DefaultUsage extractUsage(JsonNode root) {
        JsonNode usage = root.get("usage");
        if (usage == null) {
            return null;
        }
        return new DefaultUsage(
            usage.get("prompt_tokens").asInt(0),
            usage.get("completion_tokens").asInt(0),
            usage.get("total_tokens").asInt(0));
    }

    /**
     * 从流式 SSE chunk 提取 content delta（无内容返回 null）。
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

    /**
     * 从流式 usage chunk 解析 token 用量。
     */
    private DefaultUsage parseUsageFromChunk(String chunkData) {
        try {
            JsonNode root = objectMapper.readTree(chunkData);
            JsonNode usage = root.get("usage");
            if (usage != null) {
                return new DefaultUsage(
                    usage.get("prompt_tokens").asInt(0),
                    usage.get("completion_tokens").asInt(0),
                    usage.get("total_tokens").asInt(0));
            }
        } catch (Exception e) {
            log.warn("解析流式 usage chunk 失败: {}", chunkData, e);
        }
        return null;
    }
}
