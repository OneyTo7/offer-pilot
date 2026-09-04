package com.eyki.offerpilot.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 博查 AI 搜索 API 配置。
 * <p>
 * 在 application.yaml 中配置：
 * <pre>
 * bocha:
 *   api-key: ${BOCHA_API_KEY:}
 * </pre>
 */
@ConfigurationProperties(prefix = "bocha")
public class BochaProperties {

    /**
     * 博查 AI 搜索 API Key。
     * 从 https://open.bochaai.com 注册获取。
     */
    private String apiKey = "";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}