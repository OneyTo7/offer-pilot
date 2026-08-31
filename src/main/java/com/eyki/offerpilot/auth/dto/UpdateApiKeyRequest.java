package com.eyki.offerpilot.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateApiKeyRequest {

    @Size(max = 255, message = "API Key 长度不能超过255个字符")
    private String apiKey;
}