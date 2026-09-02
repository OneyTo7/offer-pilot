package com.eyki.offerpilot.knowledge.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识文档上传请求。
 */
@Data
public class KnowledgeUploadRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题长度不能超过 255 字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 500000, message = "内容长度不能超过 50 万字符（约 5MB 上限，与文件上传一致）")
    private String content;

    /** 内容类型：text（默认）/ file / url */
    private String contentType = "text";
}