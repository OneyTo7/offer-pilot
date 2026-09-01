package com.eyki.offerpilot.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Interview question view object returned to frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestionVO {

    private Long id;
    private String text;
    private String answer;
    private String feedback;
    private Double score;
    private String status;
}