package com.eyki.offerpilot.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * SSE event sent to the frontend during interview.
 */
@Data
@AllArgsConstructor
public class InterviewEvent {

    /** Event type: feedback, next_question, round_end, complete, error */
    private String type;

    /** Event data (question text, feedback content, etc.) */
    private String data;

    /** Round number (1-3), only for next_question events */
    private Integer round;

    /** Question index (1-10), only for next_question events */
    private Integer questionIndex;

    /** Question ID for answering, only for next_question events */
    private Long questionId;

    public static InterviewEvent feedback(String content) {
        return new InterviewEvent("feedback", content, null, null, null);
    }

    public static InterviewEvent nextQuestion(Long questionId, Integer round, Integer questionIndex,
        String questionText) {
        return new InterviewEvent("next_question", questionText, round, questionIndex, questionId);
    }

    public static InterviewEvent roundEnd(Integer round, String summary) {
        return new InterviewEvent("round_end", summary, round, null, null);
    }

    public static InterviewEvent complete(String summary) {
        return new InterviewEvent("complete", summary, null, null, null);
    }

    public static InterviewEvent error(String message) {
        return new InterviewEvent("error", message, null, null, null);
    }
}