package com.eyki.offerpilot.interview.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuestionStatus {

    PENDING(0, "待答"),
    ANSWERED(1, "已答"),
    SKIPPED(2, "已跳过");

    private final int code;
    private final String desc;

    public static QuestionStatus fromCode(int code) {
        for (QuestionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown question status code: " + code);
    }
}