package com.eyki.offerpilot.resume.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目经历。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeProject {

    private String name;
    private String role;
    private String startDate;
    private String endDate;
    private String description;
    private String responsibilities;
    private List<String> technologies;
    private String highlights;
}