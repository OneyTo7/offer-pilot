package com.eyki.offerpilot.aicore.service.impl;

import com.eyki.offerpilot.aicore.service.AiService;
import com.eyki.offerpilot.common.exception.BusinessException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI service implementation. Wraps Spring AI's ChatClient to provide synchronous
 * and streaming LLM invocations. Supports multiple model providers via OpenAI-compatible API.
 *
 * <p>统一走 ChatClient + advisor 链：用户自备 API Key 时通过 context 的 {@code api_key}
 * 激活 {@code ApiKeyRoutingAdvisor} 拦截模型调用并直连第三方 API（支持 DeepSeek/通义千问/GLM
 * 等，通过 {@code api_base_url} 和 {@code api_model} 切换）；平台 key 时透传给默认 ChatModel。
 * 三条路径共享安全校验、记忆注入、RAG、日志与用量控制。</p>
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    /** Context key for routing to user-provided DeepSeek API key (ApiKeyRoutingAdvisor). */
    private static final String CTX_API_KEY = "api_key";

    private final ChatClient chatClient;
    private final ChatClient chatClientNoRag;

    public AiServiceImpl(ChatClient chatClient, ChatClient chatClientNoRag) {
        this.chatClient = chatClient;
        this.chatClientNoRag = chatClientNoRag;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, String apiKey, Map<String, Object> context) {
        try {
            Map<String, Object> finalContext = mergeContext(context, apiKey);
            var spec = chatClient.prompt().system(systemPrompt).user(userPrompt);
            if (finalContext != null && !finalContext.isEmpty()) {
                spec.advisors(advisor -> finalContext.forEach(advisor::param));
            }
            ChatResponse chatResponse = spec.call().chatResponse();
            String response = chatResponse.getResult().getOutput().getText();

            if (response == null) {
                throw BusinessException.aiServiceError("AI 服务返回为空");
            }

            // token 用量由 TokenUsageAdvisor 前置校验 + 后置累计（两条路径统一）
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 服务调用异常", e);
            throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt, String apiKey, Map<String, Object> context) {
        try {
            Map<String, Object> finalContext = mergeContext(context, apiKey);
            var spec = chatClient.prompt().system(systemPrompt).user(userPrompt);
            if (finalContext != null && !finalContext.isEmpty()) {
                spec.advisors(advisor -> finalContext.forEach(advisor::param));
            }
            // token 用量由 TokenUsageAdvisor 前置校验 + 后置累计
            // （流式 usage 位于最后一个 chunk 的 metadata，advisor 内已处理）
            return spec.stream().chatResponse()
                .map(response -> response.getResult().getOutput().getText())
                .onErrorResume(e -> {
                    log.error("AI 流式服务调用异常", e);
                    return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
                });
        } catch (Exception e) {
            log.error("AI 流式服务调用异常", e);
            return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
        }
    }

    @Override
    public <T> T chatWithEntity(String systemPrompt, String userPrompt, String apiKey, Class<T> entityClass,
        Map<String, Object> context) {
        // 统一路径：ChatClient + BeanOutputConverter（JSON Schema 由 converter 自动生成并拼入 system prompt）
        try {
            Map<String, Object> finalContext = mergeContext(context, apiKey);
            BeanOutputConverter<T> converter = new BeanOutputConverter<>(entityClass);
            String fullSystemPrompt = systemPrompt + "\n\n" + converter.getFormat();

            var spec = chatClient.prompt().system(fullSystemPrompt).user(userPrompt);
            if (finalContext != null && !finalContext.isEmpty()) {
                spec.advisors(advisor -> finalContext.forEach(advisor::param));
            }
            ChatResponse chatResponse = spec.call().chatResponse();
            String content = chatResponse.getResult().getOutput().getText();
            if (content == null) {
                throw BusinessException.aiServiceError("AI 结构化输出返回为空");
            }

            // token 用量由 TokenUsageAdvisor 前置校验 + 后置累计（两条路径统一）
            return converter.convert(content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 结构化输出调用异常", e);
            throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public <T> T chatWithEntityNoRag(String systemPrompt, String userPrompt, Class<T> entityClass,
        Map<String, Object> context) {
        // Uses the dedicated no-RAG ChatClient (SafeValid → TokenUsage → ReReading → Log → ApiKeyRouting)
        // context 需传 "user_id" 以激活 TokenUsageAdvisor 的额度校验与用量累计
        try {
            var spec = chatClientNoRag.prompt().system(systemPrompt).user(userPrompt);
            if (context != null && !context.isEmpty()) {
                spec.advisors(advisor -> context.forEach(advisor::param));
            }
            ChatResponse chatResponse = spec.call().chatResponse();
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
     * 合并调用方 context 与用户 API key：api_key 以 {@code api_key} 键并入 context，
     * 由 ApiKeyRoutingAdvisor 读取（null/blank 时不并入，透传平台 key）。
     */
    private Map<String, Object> mergeContext(Map<String, Object> context, String apiKey) {
        boolean hasContext = context != null && !context.isEmpty();
        boolean hasApiKey = apiKey != null && !apiKey.isBlank();
        if (!hasContext && !hasApiKey) {
            return null;
        }
        Map<String, Object> merged = new HashMap<>();
        if (hasContext) {
            merged.putAll(context);
        }
        if (hasApiKey) {
            merged.put(CTX_API_KEY, apiKey.trim());
        }
        return merged;
    }
}
