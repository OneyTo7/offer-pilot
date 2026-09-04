package com.eyki.offerpilot.aicore.tool;

import com.eyki.offerpilot.common.config.BochaProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 博查 Web Search 集成工具。
 * <p>
 * 通过 {@link Tool} 注解暴露为 AI Agent 可调用的工具，当面试问答或报告生成
 * 需要实时信息、最新技术动态时，由 LLM 自主决定是否调用。
 * <p>
 * API 文档：https://open.bochaai.com
 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final String BOCHA_API_URL = "https://api.bochaai.com/v1/web-search";
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public WebSearchTool(BochaProperties bochaProperties, ObjectMapper objectMapper) {
        this.apiKey = bochaProperties.getApiKey();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
            .build();
    }

    /**
     * 搜索互联网信息，获取最新的网页内容和摘要。
     * 当用户询问实时信息、最新新闻、技术动态时使用。
     *
     * @param query 搜索关键词
     * @return 搜索结果文本
     */
    @Tool(name = "web_search", description = "搜索互联网获取实时信息、最新新闻、技术文档等。当用户询问需要联网获取最新信息时使用此工具。")
    public String search(String query) {
        return search(query, 10);
    }

    /**
     * 搜索互联网信息，可指定返回结果数量。
     *
     * @param query 搜索关键词
     * @param count 返回结果条数（1-50）
     * @return 搜索结果文本
     */
    @Tool(name = "web_search_with_count", description = "搜索互联网，可指定返回结果数量（1-50条）。当用户需要更全面或更精确的搜索结果时使用。")
    public String search(String query, int count) {
        if (apiKey == null || apiKey.isBlank()) {
            return "【搜索工具未配置】请先配置 Bocha API Key（bocha.api-key）";
        }

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> requestBody = Map.of(
                "query", query,
                "count", Math.min(Math.max(count, 1), 50),
                "summary", true
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BOCHA_API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            long elapsed = System.currentTimeMillis() - start;
            log.info("Bocha search | query={} | status={} | elapsed={}ms", query, response.statusCode(), elapsed);

            if (response.statusCode() != 200) {
                log.error("Bocha API error | status={} | body={}", response.statusCode(), response.body());
                return "搜索服务暂时不可用（状态码: " + response.statusCode() + "）";
            }

            Map<String, Object> result = objectMapper.readValue(
                response.body(), new TypeReference<>() {});
            return formatResults(result);

        } catch (Exception e) {
            log.error("Bocha search failed | query={}", query, e);
            return "搜索失败: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private String formatResults(Map<String, Object> result) {
        // 博查 API 返回：{ code: 200, data: { webPages: { value: [...] } } }
        Object codeObj = result.get("code");
        if (codeObj instanceof Number code && code.intValue() != 200) {
            return "搜索服务返回异常（code: " + code.intValue() + "）";
        }

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        if (data == null) {
            return "搜索服务返回数据为空。";
        }

        Map<String, Object> webPages = (Map<String, Object>) data.get("webPages");
        if (webPages == null) {
            return "未找到相关搜索结果。";
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) webPages.get("value");
        if (items == null || items.isEmpty()) {
            return "未找到相关搜索结果。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共找到 ").append(items.size()).append(" 条结果：\n\n");

        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            sb.append(i + 1).append(". ").append(item.getOrDefault("name", "无标题"));
            sb.append("\n   链接: ").append(item.getOrDefault("url", ""));
            sb.append("\n   摘要: ").append(item.getOrDefault("snippet", ""));
            if (item.get("datePublished") != null) {
                sb.append("\n   日期: ").append(item.get("datePublished"));
            }
            sb.append("\n\n");
        }
        sb.append("--- 以上为搜索结果，如需更详细信息请访问对应链接 ---");

        return sb.toString();
    }
}