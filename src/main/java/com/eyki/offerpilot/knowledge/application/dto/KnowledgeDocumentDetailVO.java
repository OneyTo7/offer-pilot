package com.eyki.offerpilot.knowledge.application.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档详情返回 VO（含全文内容）。
 */
@Data
@Builder
public class KnowledgeDocumentDetailVO {

    private Long id;
    private String title;
    private String content;
    private String contentType;
    private String fileUrl;
    private Integer chunkCount;
    private Integer status;
    private String statusDesc;
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}