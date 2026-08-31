package com.eyki.offerpilot.knowledge.domain;

/**
 * 文档索引状态 — Value Object。
 *
 * 状态机：INDEXING → COMPLETED 或 INDEXING → FAILED（终态，不可回转）。
 */
public enum DocumentStatus {

    INDEXING(0, "索引中"), COMPLETED(1, "已完成"), FAILED(2, "失败");

    private final int code;
    private final String description;

    DocumentStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static DocumentStatus fromCode(int code) {
        for (DocumentStatus ds : values()) {
            if (ds.code == code) {
                return ds;
            }
        }
        throw new IllegalArgumentException("未知的文档状态码: " + code);
    }
}