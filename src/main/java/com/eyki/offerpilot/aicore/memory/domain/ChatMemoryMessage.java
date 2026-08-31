package com.eyki.offerpilot.aicore.memory.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_memory")
public class ChatMemoryMessage {

    @TableId
    private Long id;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("user_id")
    private Long userId;

    @TableField("message_type")
    private String messageType;

    private String content;

    @TableField("metadata")
    private String metadata;

    @TableField("created_at")
    private LocalDateTime createdAt;
}