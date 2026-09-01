package com.eyki.offerpilot.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportVO {

    private Long id;

    @JsonProperty("resume_id")
    private Long resumeId;

    @JsonProperty("resume_name")
    private String resumeName;

    @JsonProperty("position_id")
    private Long positionId;

    @JsonProperty("position_title")
    private String positionTitle;

    private ReportContent content;

    private Integer status;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}