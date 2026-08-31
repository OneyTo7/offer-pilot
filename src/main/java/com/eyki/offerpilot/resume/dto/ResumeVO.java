package com.eyki.offerpilot.resume.dto;

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
public class ResumeVO {

    private Long id;

    private String name;

    @JsonProperty("file_url")
    private String fileUrl;

    @JsonProperty("file_size")
    private Integer fileSize;

    @JsonProperty("page_count")
    private Integer pageCount;

    private Integer status;

    @JsonProperty("is_default")
    private Integer isDefault;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}