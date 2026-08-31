package com.eyki.offerpilot.knowledge.application.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档列表返回 VO。
 */
@Data
@Builder
public class KnowledgeDocumentVO {

    private Long id;
    private String title;
    private String contentType;
    private String fileUrl;
    private Integer chunkCount;
    private Integer status;
    private String statusDesc;
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}