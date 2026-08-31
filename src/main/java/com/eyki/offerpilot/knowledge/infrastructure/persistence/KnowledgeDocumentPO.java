package com.eyki.offerpilot.knowledge.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档 — 持久化对象。
 *
 * 与数据库表一一对应，携带 MyBatis-Plus 框架注解。
 * 与领域对象 {@link com.eyki.offerpilot.knowledge.domain.KnowledgeDocument} 分离，
 * 避免框架污染纯领域模型。
 */
@Data
@TableName("knowledge_base")
public class KnowledgeDocumentPO {

    @TableId
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String title;

    private String content;

    @TableField("content_type")
    private String contentType;

    @TableField("file_url")
    private String fileUrl;

    @TableField("chunk_count")
    private Integer chunkCount;

    private Integer status;

    @TableField("fail_reason")
    private String failReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}