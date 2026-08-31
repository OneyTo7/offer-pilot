package com.eyki.offerpilot.resume.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResumeStatus {

    PARSING(0, "解析中"), COMPLETED(1, "解析完成"), FAILED(2, "解析失败");

    private final int code;
    private final String desc;

    public static ResumeStatus fromCode(int code) {
        for (ResumeStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown resume status code: " + code);
    }
}