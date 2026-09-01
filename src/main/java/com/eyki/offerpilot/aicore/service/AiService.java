package com.eyki.offerpilot.aicore.service;

import java.util.Map;
import reactor.core.publisher.Flux;

/**
 * AI service abstraction over Spring AI's ChatClient.
 *
 * <p><b>双路径设计：</b>
 * <ul>
 *   <li><b>平台 key</b>（apiKey 为 null/blank）：走 ChatClient，advisor 链全部生效
 *       （安全校验 → 重读指令 → 对话记忆 → 自动 RAG → 日志），通过 context 控制</li>
 *   <li><b>用户 API key</b>：直接 HTTP 调用 DeepSeek（OpenAI 兼容接口），advisors 不适用，
 *       调用方需自行处理 RAG（服务层手动检索拼入 prompt）</li>
 * </ul>
 */
public interface AiService {

    /**
     * Chat 请求（含 advisor context）。
     * <p>
     * 平台 key 路径的 context 支持：
     * <ul>
     *   <li>{@code "conversation_id"} — MessageChatMemoryAdvisor 注入/保存对话历史</li>
     *   <li>{@code "vector_store_filter_expression"} — RetrievalAugmentationAdvisor 的用户级 RAG 隔离</li>
     * </ul>
     *
     * @param apiKey  用户的 DeepSeek API key（null/blank → 平台 key，ChatClient 路径）
     * @param context advisor context 参数（用户 API key 路径忽略）
     */
    String chat(String systemPrompt, String userPrompt, String apiKey, Map<String, Object> context);

    /**
     * 流式 Chat 请求（含 advisor context），用于 SSE 场景。语义同 {@link #chat}。
     */
    Flux<String> chatStream(String systemPrompt, String userPrompt, String apiKey, Map<String, Object> context);

    /**
     * 结构化输出请求（含 advisor context）。
     * <p>
     * 平台 key 路径使用 {@code ChatClient.call().entity()}；用户 API key 路径使用
     * {@code BeanOutputConverter}。两条路径都自动生成 JSON Schema 约束并解析为目标类型。
     */
    <T> T chatWithEntity(String systemPrompt, String userPrompt, String apiKey, Class<T> entityClass,
        Map<String, Object> context);

    /**
     * 无 RAG 的结构化输出 — 纯提取场景（如简历解析）。
     * <p>
     * RetrievalAugmentationAdvisor 没有按调用禁用的开关，知识库注入会污染解析结果，
     * 因此使用独立的无 RAG ChatClient（SafeValid → ReReading → Log）。
     */
    <T> T chatWithEntityNoRag(String systemPrompt, String userPrompt, Class<T> entityClass);
}
