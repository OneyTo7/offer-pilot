package com.eyki.offerpilot.report.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("reports")
public class Report {

    @TableId
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("resume_id")
    private Long resumeId;

    @TableField("position_id")
    private Long positionId;

    @TableField("match_score")
    private BigDecimal matchScore;

    @TableField("tech_stack_analysis")
    private String techStackAnalysis;

    private String highlights;

    private String weaknesses;

    @TableField("full_report")
    private String fullReport;

    private Integer status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}