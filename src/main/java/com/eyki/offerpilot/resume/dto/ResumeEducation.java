package com.eyki.offerpilot.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 教育经历。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeEducation {

    private String school;
    private String major;
    private String degree;
    private String startDate;
    private String endDate;
    private Boolean isFullTime;
    private String description;
}