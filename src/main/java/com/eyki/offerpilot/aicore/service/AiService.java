package com.eyki.offerpilot.aicore.service;

import java.util.Map;
import reactor.core.publisher.Flux;

/**
 * AI service abstraction over Spring AI's ChatClient.
 *
 * <p><b>统一路径设计：</b>所有调用都走 ChatClient + advisor 链。apiKey 非空时，
 * 由 {@code AiServiceImpl} 将 {@code api_key} 并入 context，激活
 * {@code ApiKeyRoutingAdvisor} 拦截模型调用直连 DeepSeek；apiKey 为 null/blank 时
 * 透传给默认 ChatModel（平台 key）。两条路径共享安全校验、重读指令、对话记忆、
 * 自动 RAG、日志与 token 用量控制。</p>
 */
public interface AiService {

    /**
     * Chat 请求（含 advisor context）。
     * <p>
     * context 支持：
     * <ul>
     *   <li>{@code "chat_memory_conversation_id"} — MessageChatMemoryAdvisor 注入/保存对话历史</li>
     *   <li>{@code "vector_store_filter_expression"} — RetrievalAugmentationAdvisor 的用户级 RAG 隔离</li>
     *   <li>{@code "user_id"} — TokenUsageAdvisor 的前置额度校验与后置用量累计</li>
     * </ul>
     *
     * @param apiKey  用户的 DeepSeek API key（null/blank → 平台 key）
     * @param context advisor context 参数
     */
    String chat(String systemPrompt, String userPrompt, String apiKey, Map<String, Object> context);

    /**
     * 流式 Chat 请求（含 advisor context），用于 SSE 场景。语义同 {@link #chat}。
     */
    Flux<String> chatStream(String systemPrompt, String userPrompt, String apiKey, Map<String, Object> context);

    /**
     * 结构化输出请求（含 advisor context）。
     * <p>
     * 使用 {@code ChatClient.call().chatResponse()} + {@code BeanOutputConverter}，
     * JSON Schema 由 converter 自动生成并拼入 system prompt，两种 key 路径行为一致。
     */
    <T> T chatWithEntity(String systemPrompt, String userPrompt, String apiKey, Class<T> entityClass,
        Map<String, Object> context);

    /**
     * 无 RAG 的结构化输出 — 纯提取场景（如简历解析）。
     * <p>
     * RetrievalAugmentationAdvisor 没有按调用禁用的开关，知识库注入会污染解析结果，
     * 因此使用独立的无 RAG ChatClient（SafeValid → TokenUsage → ReReading → Log → ApiKeyRouting）。
     * context 需传 {@code "user_id"} 以激活 TokenUsageAdvisor 的额度前置校验与用量后置累计。
     */
    <T> T chatWithEntityNoRag(String systemPrompt, String userPrompt, Class<T> entityClass,
        Map<String, Object> context);
}
