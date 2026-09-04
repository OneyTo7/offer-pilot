package com.eyki.offerpilot.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateApiKeyRequest {

    @Size(max = 255, message = "API Key 长度不能超过255个字符")
    private String apiKey;

    /** API 服务商 base URL（null 表示使用平台默认） */
    @Size(max = 255, message = "API Base URL 长度不能超过255个字符")
    private String apiBaseUrl;

    /** 模型名称（null 表示使用平台默认） */
    @Size(max = 100, message = "模型名称长度不能超过100个字符")
    private String apiModel;
}