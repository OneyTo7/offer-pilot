package com.eyki.offerpilot.position.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PositionRequest {

    @NotNull(message = "简历ID不能为空")
    @JsonProperty("resume_id")
    private Long resumeId;

    @NotBlank(message = "职位名称不能为空")
    @Size(max = 200, message = "职位名称长度不能超过200个字符")
    private String title;

    @Size(max = 200, message = "公司名称长度不能超过200个字符")
    private String company;

    @NotBlank(message = "职位描述不能为空")
    @JsonProperty("jd_text")
    private String jdText;

    @Size(max = 100, message = "工作地点长度不能超过100个字符")
    private String location;

    @Size(max = 50, message = "薪资范围长度不能超过50个字符")
    @JsonProperty("salary_range")
    private String salaryRange;
}