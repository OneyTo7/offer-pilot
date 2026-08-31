package com.eyki.offerpilot.interview.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

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