package com.eyki.offerpilot.position.dto;

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
public class PositionVO {

    private Long id;

    @JsonProperty("resume_id")
    private Long resumeId;

    private String title;

    private String company;

    @JsonProperty("jd_text")
    private String jdText;

    private String location;

    @JsonProperty("salary_range")
    private String salaryRange;

    @JsonProperty("is_default")
    private Integer isDefault;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}