package com.eyki.offerpilot.resume.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历详情 VO。
 *
 * JSON 字段已解析为结构化的 Java 对象，前端直接使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeDetailVO {

    private Long id;

    private String name;

    @JsonProperty("file_url")
    private String fileUrl;

    @JsonProperty("file_size")
    private Integer fileSize;

    @JsonProperty("page_count")
    private Integer pageCount;

    /** 全文原始文本 */
    @JsonProperty("parsed_text")
    private String parsedText;

    /** 基本信息 */
    @JsonProperty("basic_info")
    private ResumeBasicInfo basicInfo;

    /** 教育经历 */
    private List<ResumeEducation> education;

    /** 工作经历 */
    @JsonProperty("work_experience")
    private List<ResumeWorkExperience> workExperience;

    /** 项目经历 */
    private List<ResumeProject> projects;

    /** 技能标签 */
    private List<ResumeSkill> skills;

    /** 证书/语言 */
    private List<ResumeCertificate> certificates;

    /** 简历摘要 */
    private String summary;

    @JsonProperty("is_default")
    private Integer isDefault;

    private Integer status;

    @JsonProperty("fail_reason")
    private String failReason;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}