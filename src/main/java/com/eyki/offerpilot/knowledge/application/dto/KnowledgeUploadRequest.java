package com.eyki.offerpilot.knowledge.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 知识文档上传请求。
 */
@Data
public class KnowledgeUploadRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    /** 内容类型：text（默认）/ file / url */
    private String contentType = "text";
}