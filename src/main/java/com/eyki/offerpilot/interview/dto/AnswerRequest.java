package com.eyki.offerpilot.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AnswerRequest {

    /** sessionId is set from URL path by the controller, not from request body */
    private Long sessionId;

    @NotNull(message = "题目 ID 不能为空")
    private Long questionId;

    @NotBlank(message = "回答内容不能为空")
    private String answer;
}