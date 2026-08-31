package com.eyki.offerpilot.resume.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 简历——持久化实体。
 *
 * JSON 字段以 String 形式存储，在 Service 层通过 Hutool JSONUtil 与 Java 对象互转。
 */
@Data
@TableName("resumes")
public class Resume {

    @TableId
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String name;

    @TableField("file_url")
    private String fileUrl;

    @TableField("file_size")
    private Integer fileSize;

    @TableField("page_count")
    private Integer pageCount;

    /** 全文原始文本（PDFBox / Tess4j 提取） */
    @TableField("parsed_text")
    private String parsedText;

    /** 基本信息 JSON */
    @TableField("basic_info")
    private String basicInfo;

    /** 教育经历 JSON */
    @TableField("education")
    private String education;

    /** 工作经历 JSON */
    @TableField("work_experience")
    private String workExperience;

    /** 项目经历 JSON */
    @TableField("projects")
    private String projects;

    /** 技能标签 JSON */
    @TableField("skills")
    private String skills;

    /** 证书/语言 JSON */
    @TableField("certificates")
    private String certificates;

    /** 简历摘要 */
    private String summary;

    /** AI 原始返回 JSON（调试用） */
    @TableField("raw_response")
    private String rawResponse;

    @TableField("is_default")
    private Integer isDefault;

    private Integer status;

    @TableField("fail_reason")
    private String failReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}