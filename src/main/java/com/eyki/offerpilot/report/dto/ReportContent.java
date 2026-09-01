package com.eyki.offerpilot.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评估报告内容 DTO。
 *
 * <p>包含 AI 生成的完整分析结果。analysis_data 字段存储原始 AI 响应 JSON，
 * 前端直接解析渲染，后端仅做透传。旧字段（matchScore, techStackAnalysis 等）保留
 * 用于向后兼容，新报告优先使用 analysis_data。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportContent {

    @JsonProperty("match_score")
    private Double matchScore;

    @JsonProperty("tech_stack_analysis")
    private TechStackAnalysis techStackAnalysis;

    private List<String> highlights;

    private List<String> weaknesses;

    @JsonProperty("full_report")
    private String fullReport;

    @JsonProperty("analysis_data")
    private String analysisData;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TechStackAnalysis {
        private String matched;
        private String missing;
        private String recommendation;
    }
}