package com.eyki.offerpilot.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Interview session view object for listing and detail.
 */
@Data
@Builder
public class SessionVO {

    private Long id;
    private Long resumeId;
    private Long positionId;
    private Integer currentRound;
    private Integer currentQuestion;
    private Integer totalQuestions;
    private Integer status;
    private Integer durationSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
}