package com.eyki.offerpilot.knowledge.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 知识库语义搜索请求。
 */
@Data
public class KnowledgeSearchRequest {

    @NotBlank(message = "搜索关键词不能为空")
    private String query;

    /** 返回结果数量上限，默认 5 */
    private Integer topK = 5;
}