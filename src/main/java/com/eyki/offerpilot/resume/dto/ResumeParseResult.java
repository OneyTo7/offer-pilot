package com.eyki.offerpilot.resume.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 简历 AI 解析结果数据结构。
 *
 * <p>与 AI 输出 JSON 结构一一对应，由 {@code BeanOutputConverter} 自动生成 JSON Schema，
 * 约束 AI 输出格式，并自动解析响应（含 markdown 代码块剥离）。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeParseResult(
    @JsonProperty("basic_info") BasicInfo basicInfo,
    List<Education> education,
    @JsonProperty("work_experience") List<WorkExperience> workExperience,
    List<Project> projects,
    List<SkillGroup> skills,
    List<Certificate> certificates,
    String summary
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BasicInfo(
        String name,
        String gender,
        @JsonProperty("birthDate") String birthDate,
        String phone,
        String email,
        String location,
        @JsonProperty("expectedPosition") String expectedPosition,
        @JsonProperty("expectedSalary") String expectedSalary,
        @JsonProperty("workYears") Double workYears,
        @JsonProperty("highestDegree") String highestDegree,
        @JsonProperty("politicalStatus") String politicalStatus,
        @JsonProperty("currentCompany") String currentCompany,
        @JsonProperty("currentPosition") String currentPosition
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Education(
        String school,
        String major,
        String degree,
        @JsonProperty("startDate") String startDate,
        @JsonProperty("endDate") String endDate,
        @JsonProperty("isFullTime") Boolean isFullTime,
        String description
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkExperience(
        String company,
        String position,
        String department,
        @JsonProperty("startDate") String startDate,
        @JsonProperty("endDate") String endDate,
        @JsonProperty("isCurrent") Boolean isCurrent,
        String responsibilities,
        List<String> achievements,
        List<String> technologies
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(
        String name,
        String role,
        @JsonProperty("startDate") String startDate,
        @JsonProperty("endDate") String endDate,
        String description,
        String responsibilities,
        List<String> technologies,
        String highlights
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillGroup(
        String category,
        List<String> skills
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Certificate(
        String name,
        String date,
        String issuer,
        String type,
        String level
    ) {}
}