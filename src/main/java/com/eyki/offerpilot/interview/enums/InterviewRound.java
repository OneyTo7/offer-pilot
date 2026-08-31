package com.eyki.offerpilot.interview.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewRound {

    FIRST(1, "一面", "基础技术能力考察"), SECOND(2, "二面", "项目经验与深度技术考察"),
    THIRD(3, "三面", "系统设计与综合能力考察");

    private final int code;
    private final String name;
    private final String description;

    public static InterviewRound fromCode(int code) {
        for (InterviewRound round : values()) {
            if (round.code == code) {
                return round;
            }
        }
        throw new IllegalArgumentException("Unknown interview round code: " + code);
    }
}