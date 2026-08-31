package com.eyki.offerpilot.interview.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SessionStatus {

    IN_PROGRESS(0, "进行中"),
    COMPLETED(1, "已完成"),
    EXPIRED(2, "已过期"),
    ABANDONED(3, "已中断");

    private final int code;
    private final String desc;

    public static SessionStatus fromCode(int code) {
        for (SessionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown session status code: " + code);
    }
}