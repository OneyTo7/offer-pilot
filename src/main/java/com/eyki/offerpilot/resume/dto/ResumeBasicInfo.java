package com.eyki.offerpilot.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历基本信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeBasicInfo {

    private String name;
    private String gender;
    private String birthDate;
    private String phone;
    private String email;
    private String location;
    private String expectedPosition;
    private String expectedSalary;
    private Double workYears;
    private String highestDegree;
    private String politicalStatus;
    private String currentCompany;
    private String currentPosition;
}