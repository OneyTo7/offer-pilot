--changeset init:004
CREATE TABLE reports
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT   NOT NULL,
    resume_id           BIGINT   NOT NULL,
    position_id         BIGINT   NOT NULL,
    match_score         DECIMAL(5, 2)     DEFAULT NULL COMMENT '匹配度百分比，如 72.00',
    tech_stack_analysis JSON              DEFAULT NULL COMMENT '技术栈分析 JSON',
    highlights          JSON              DEFAULT NULL COMMENT '亮点提炼 JSON 数组',
    weaknesses          JSON              DEFAULT NULL COMMENT '短板提醒 JSON 数组',
    full_report         LONGTEXT          DEFAULT NULL COMMENT '报告原始文本',
    status              TINYINT           DEFAULT 0 COMMENT '0-生成中 1-完成 2-失败',
    error_message       VARCHAR(500)      DEFAULT NULL COMMENT '失败原因',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX               idx_user_id (user_id),
    INDEX               idx_resume_id (resume_id),
    INDEX               idx_position_id (position_id),
    INDEX               idx_status (status),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (resume_id) REFERENCES resumes (id),
    FOREIGN KEY (position_id) REFERENCES target_positions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估报告表';