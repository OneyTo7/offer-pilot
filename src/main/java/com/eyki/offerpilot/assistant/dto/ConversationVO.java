package com.eyki.offerpilot.assistant.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ConversationVO {

    private Long id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}