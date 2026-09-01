-- 为评估报告表添加 analysis_data 字段，存储完整的 AI 多维度分析结果 JSON
ALTER TABLE reports
    ADD COLUMN analysis_data LONGTEXT DEFAULT NULL COMMENT '完整的 AI 分析结果 JSON，包含技能深度分析、项目评估、提升路线图等多维度信息'
    AFTER full_report;