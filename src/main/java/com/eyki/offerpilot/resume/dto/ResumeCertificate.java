package com.eyki.offerpilot.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 证书/语言能力。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeCertificate {

    private String name;
    private String date;
    private String issuer;
    private String type; // certificate / language
    private String level; // 语言等级，如 CET-6
}