package com.eyki.offerpilot.interview.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Interview summary returned after completion.
 */
@Data
@Builder
public class InterviewSummaryVO {

    private Long sessionId;
    private Integer totalRounds;
    private Integer totalQuestions;
    private Integer answeredQuestions;
    private Integer skippedQuestions;
    private Integer durationSeconds;
    private String summary;
    private List<RoundSummary> rounds;

    @Data
    @Builder
    public static class RoundSummary {
        private Integer round;
        private String roundName;
        private Integer totalQuestions;
        private Integer answeredQuestions;
        private Integer skippedQuestions;
        private List<QuestionSummary> questions;
    }

    @Data
    @Builder
    public static class QuestionSummary {
        private Long id;
        private Integer questionIndex;
        private String questionText;
        private String userAnswer;
        private String feedback;
        private Double score;
        private String status;
    }
}