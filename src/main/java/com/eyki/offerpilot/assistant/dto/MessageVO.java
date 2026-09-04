package com.eyki.offerpilot.assistant.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MessageVO {

    private String role;       // "user" | "assistant"
    private String content;    // 消息内容
    private LocalDateTime createdAt;
}