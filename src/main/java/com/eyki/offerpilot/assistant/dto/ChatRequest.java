package com.eyki.offerpilot.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 5000, message = "消息内容不能超过5000字")
    private String message;

    /** 是否开启联网搜索 */
    @JsonProperty("search_enabled")
    private boolean searchEnabled;
}