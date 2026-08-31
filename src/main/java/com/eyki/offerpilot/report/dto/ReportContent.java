package com.eyki.offerpilot.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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