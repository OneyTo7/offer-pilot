package com.eyki.offerpilot.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportRequest {

    @NotNull(message = "简历ID不能为空")
    @JsonProperty("resume_id")
    private Long resumeId;

    @NotNull(message = "目标职位ID不能为空")
    @JsonProperty("position_id")
    private Long positionId;
}