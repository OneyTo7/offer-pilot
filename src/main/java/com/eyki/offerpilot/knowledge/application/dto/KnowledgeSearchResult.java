package com.eyki.offerpilot.knowledge.application.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 知识库语义搜索结果。
 */
@Data
@Builder
public class KnowledgeSearchResult {

    /** 命中的文档 ID */
    private Long documentId;

    /** 命中的文档标题 */
    private String title;

    /** 命中的文本片段内容 */
    private String content;

    /** 相似度分数 */
    private Double score;
}