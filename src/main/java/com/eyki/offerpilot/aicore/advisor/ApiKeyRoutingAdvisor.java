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
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

/**
 * 用户自备 API Key 的路由 Advisor — 把直连第三方 API 的逻辑下沉到 advisor 链内，
 * 使用户 key 路径与平台 key 路径共享同一套编排（安全校验、记忆注入、RAG、日志、用量控制）。
 *
 * <p>支持多模型服务商（DeepSeek/通义千问/GLM 等），通过 context 参数动态切换：
 * <ul>
 *   <li>{@code api_key} — API Key（必选，触发拦截拦截）</li>
 *   <li>{@code api_base_url} — API 基础地址（可选，默认 {@code https://api.deepseek.com/v1}）</li>
 *   <li>{@code api_model} — 模型名（可选，默认 {@code deepseek-chat}）</li>
 * </ul>
 * 最终请求地址为 {@code {api_base_url}/chat/completions}。</p>
 *
 * <p><b>拦截条件</b>：advisor context 含 {@code api_key} 时，本 advisor 不走
 * {@code ChatModelCallAdvisor}（平台 ChatModel 绑定的是平台 key），而是直连
 * 第三方 OpenAI 兼容接口并构造响应；未传 api_key 时 {@code chain.nextCall()} 透传。</p>
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
    private static final String CTX_API_BASE_URL = "api_base_url";
    private static final String CTX_API_MODEL = "api_model";

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
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
        // 用户 key：拦截模型调用，直连第三方 API
        String apiBaseUrl = extractApiBaseUrl(request);
        String apiModel = extractApiModel(request);
        try {
            String jsonBody = buildRequestBody(request.prompt(), apiModel);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + CHAT_COMPLETIONS_PATH))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                String errorBody = response.body();
                log.error("AI 服务返回异常状态码: status={}, body={}", response.statusCode(), errorBody);
                if (isInsufficientBalance(errorBody)) {
                    throw BusinessException.apiKeyInsufficientBalance();
                }
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
        // 用户 key：直连第三方 API 流式接口，SSE 逐 chunk 转发
        String apiBaseUrl = extractApiBaseUrl(request);
        String apiModel = extractApiModel(request);
        return Flux.create(emitter -> {
            // 使用数组持有 response 引用，以便在取消时关闭连接
            final HttpResponse<InputStream>[] responseRef = new HttpResponse[1];

            emitter.onCancel(() -> {
                log.info("AI 流式请求被取消 (用户 API Key)");
                if (responseRef[0] != null && responseRef[0].body() != null) {
                    try { responseRef[0].body().close(); } catch (Exception ignored) {}
                }
            });
            emitter.onDispose(() -> {
                if (responseRef[0] != null && responseRef[0].body() != null) {
                    try { responseRef[0].body().close(); } catch (Exception ignored) {}
                }
            });

            try {
                String jsonBody = buildRequestBody(request.prompt(), true, apiModel);
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + CHAT_COMPLETIONS_PATH))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(STREAM_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

                HttpResponse<InputStream> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream());
                responseRef[0] = response;

                if (response.statusCode() != 200) {
                    String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    log.error("AI 流式服务返回异常状态码: status={}, body={}", response.statusCode(), errorBody);
                    if (isInsufficientBalance(errorBody)) {
                        emitter.error(BusinessException.apiKeyInsufficientBalance());
                    } else {
                        emitter.error(BusinessException.aiServiceError("AI 服务调用失败，状态码: " + response.statusCode()));
                    }
                    return;
                }

                // DeepSeek 流式响应的最后一条 data 包含 usage，格式为：
                // data: {"choices":[],"usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150}}
                DefaultUsage lastUsage = null;

                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 检查 emitter 是否已被取消（客户端断开）
                        if (emitter.isCancelled()) {
                            log.info("SSE 客户端已断开，停止读取流式响应");
                            return;
                        }
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
     * 从 advisor context 提取自定义 API base URL；未传时返回默认值。
     */
    private String extractApiBaseUrl(ChatClientRequest request) {
        Object url = request.context().get(CTX_API_BASE_URL);
        if (url instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return DEFAULT_BASE_URL;
    }

    /**
     * 从 advisor context 提取自定义模型名；未传时返回默认值。
     */
    private String extractApiModel(ChatClientRequest request) {
        Object model = request.context().get(CTX_API_MODEL);
        if (model instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return DEFAULT_MODEL;
    }

    /**
     * 将增强后的 Prompt 消息列表构建为 OpenAI 兼容的 messages 数组。
     * MemoryAdvisor 注入的历史消息（user/assistant 交替）也会完整透传。
     */
    private String buildRequestBody(org.springframework.ai.chat.prompt.Prompt prompt, String apiModel) {
        return buildRequestBody(prompt, false, apiModel);
    }

    private String buildRequestBody(org.springframework.ai.chat.prompt.Prompt prompt, boolean stream, String apiModel) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("model", apiModel);
            root.put("stream", stream);

            var messages = root.putArray("messages");
            for (Message message : prompt.getInstructions()) {
                if (message instanceof ToolResponseMessage toolMsg) {
                    // Tool response: {"role": "tool", "content": "...", "tool_call_id": "..."}
                    for (var response : toolMsg.getResponses()) {
                        messages.addObject()
                            .put("role", "tool")
                            .put("content", response.responseData())
                            .put("tool_call_id", response.id());
                    }
                } else if (message instanceof AssistantMessage assistantMsg && assistantMsg.hasToolCalls()) {
                    // Assistant message with tool_calls: {"role": "assistant", "tool_calls": [...]}
                    var msgObj = messages.addObject();
                    msgObj.put("role", "assistant");
                    msgObj.put("content", message.getText() != null ? message.getText() : "");
                    var toolCallsArray = msgObj.putArray("tool_calls");
                    for (var tc : assistantMsg.getToolCalls()) {
                        var tcObj = toolCallsArray.addObject();
                        tcObj.put("id", tc.id());
                        tcObj.put("type", tc.type() != null ? tc.type() : "function");
                        var funcObj = tcObj.putObject("function");
                        funcObj.put("name", tc.name());
                        funcObj.put("arguments", tc.arguments());
                    }
                } else {
                    String role = switch (message) {
                        case SystemMessage ignored -> "system";
                        case AssistantMessage ignored -> "assistant";
                        case UserMessage ignored -> "user";
                        default -> null;
                    };
                    if (role == null || message.getText() == null) {
                        continue;
                    }
                    messages.addObject().put("role", role).put("content", message.getText());
                }
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

    /**
     * 判断 DeepSeek API 错误响应是否为余额不足。
     * <p>
     * DeepSeek 余额不足返回格式：HTTP 401/402，body 含 {@code "code": "insufficient_balance"}。
     */
    private boolean isInsufficientBalance(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.get("error");
            if (error != null) {
                String code = error.has("code") ? error.get("code").asText() : "";
                String message = error.has("message") ? error.get("message").asText() : "";
                return "insufficient_balance".equals(code) || message.toLowerCase().contains("insufficient balance");
            }
        } catch (Exception e) {
            log.warn("解析错误响应 JSON 失败: {}", responseBody, e);
        }
        return false;
    }
}
