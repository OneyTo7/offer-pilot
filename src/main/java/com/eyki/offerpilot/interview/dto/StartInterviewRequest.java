package com.eyki.offerpilot.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartInterviewRequest {

    @NotNull(message = "简历 ID 不能为空")
    private Long resumeId;

    @NotNull(message = "目标职位 ID 不能为空")
    private Long positionId;
}