package com.eyki.offerpilot.knowledge.domain;

/**
 * 内容类型 — Value Object。
 *
 * 限定知识文档的三种来源：纯文本、文件上传、URL。
 */
public enum ContentType {

    TEXT("text"),
    FILE("file"),
    URL("url");

    private final String value;

    ContentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ContentType fromValue(String value) {
        if (value == null) {
            return TEXT;
        }
        for (ContentType ct : values()) {
            if (ct.value.equals(value)) {
                return ct;
            }
        }
        throw new IllegalArgumentException("未知的内容类型: " + value);
    }
}