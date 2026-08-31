package com.eyki.offerpilot.resume.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    @JsonProperty("parsed_text")
    private String parsedText;

    @JsonProperty("tech_stack")
    private List<String> techStack;

    @JsonProperty("work_years")
    private BigDecimal workYears;

    private String education;

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