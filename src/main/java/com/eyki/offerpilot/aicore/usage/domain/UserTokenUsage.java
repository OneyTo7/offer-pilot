package com.eyki.offerpilot.aicore.usage.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户每日 LLM Token 用量记录。
 */
@Data
@TableName("user_token_usage")
public class UserTokenUsage {

    @TableId
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("usage_date")
    private LocalDate usageDate;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}