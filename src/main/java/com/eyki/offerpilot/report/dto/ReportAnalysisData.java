package com.eyki.offerpilot.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 评估报告多维度分析数据结构。
 *
 * <p>与 AI 输出 JSON 结构一一对应，由 {@code BeanOutputConverter} 自动生成 JSON Schema，
 * 用于约束 AI 输出格式，并自动解析响应。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportAnalysisData(
    @JsonProperty("match_score") int matchScore,
    @JsonProperty("score_breakdown") ScoreBreakdown scoreBreakdown,
    @JsonProperty("skill_analysis") SkillAnalysis skillAnalysis,
    @JsonProperty("project_analysis") List<ProjectAnalysis> projectAnalysis,
    @JsonProperty("experience_assessment") ExperienceAssessment experienceAssessment,
    @JsonProperty("education_assessment") EducationAssessment educationAssessment,
    @JsonProperty("competitive_advantages") List<String> competitiveAdvantages,
    List<String> weaknesses,
    @JsonProperty("improvement_roadmap") ImprovementRoadmap improvementRoadmap,
    @JsonProperty("interview_tips") List<String> interviewTips,
    @JsonProperty("full_report") String fullReport
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreBreakdown(
        @JsonProperty("skill_match") int skillMatch,
        @JsonProperty("experience_match") int experienceMatch,
        @JsonProperty("project_match") int projectMatch,
        @JsonProperty("education_match") int educationMatch
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillAnalysis(
        List<SkillItem> matched,
        List<PartialSkillItem> partial,
        List<MissingSkillItem> missing
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillItem(
        String name,
        String level,
        String relevance,
        String assessment
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PartialSkillItem(
        String name,
        String level,
        String relevance,
        String gap,
        String priority,
        String suggestion
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MissingSkillItem(
        String name,
        String relevance,
        String impact,
        String priority,
        String suggestion
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectAnalysis(
        String name,
        String relevance,
        String complexity,
        @JsonProperty("tech_stack") String techStack,
        @JsonProperty("role_assessment") String roleAssessment,
        String assessment,
        String suggestion
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExperienceAssessment(
        @JsonProperty("years_match") String yearsMatch,
        @JsonProperty("career_progression") String careerProgression,
        @JsonProperty("industry_relevance") String industryRelevance,
        String assessment
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EducationAssessment(
        String degree,
        @JsonProperty("school_tier") String schoolTier,
        @JsonProperty("major_relevance") String majorRelevance,
        String assessment
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImprovementRoadmap(
        @JsonProperty("short_term") List<String> shortTerm,
        @JsonProperty("mid_term") List<String> midTerm,
        @JsonProperty("long_term") List<String> longTerm
    ) {}
}