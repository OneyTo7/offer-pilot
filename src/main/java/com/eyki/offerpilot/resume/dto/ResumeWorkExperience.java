package com.eyki.offerpilot.resume.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作经历。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeWorkExperience {

    private String company;
    private String position;
    private String department;
    private String startDate;
    private String endDate;
    private Boolean isCurrent;
    private String responsibilities;
    private List<String> achievements;
    private List<String> technologies;
}