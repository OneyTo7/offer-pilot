package com.eyki.offerpilot.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for start-round and skip endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartRoundResponse {

    private InterviewQuestionVO question;
}