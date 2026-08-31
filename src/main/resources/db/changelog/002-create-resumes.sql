--changeset init:002
CREATE TABLE resumes (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL COMMENT '所属用户',
    name            VARCHAR(100)    NOT NULL COMMENT '简历名称，如 2026-08-31 版本',
    file_url        VARCHAR(500)    NOT NULL COMMENT 'MinIO 文件路径',
    file_size       INT             DEFAULT 0 COMMENT '文件大小(字节)',
    page_count      INT             DEFAULT 0 COMMENT 'PDF 页数',
    parsed_text     LONGTEXT        DEFAULT NULL COMMENT '百炼解析后的纯文本',
    tech_stack      JSON            DEFAULT NULL COMMENT '提取的技术栈列表',
    work_years      DECIMAL(3,1)    DEFAULT NULL COMMENT '工作年限',
    education       VARCHAR(50)     DEFAULT NULL COMMENT '最高学历',
    summary         TEXT            DEFAULT NULL COMMENT 'AI 提取的简历摘要',
    is_default      TINYINT         DEFAULT 0 COMMENT '0-否 1-默认简历',
    status          TINYINT         DEFAULT 0 COMMENT '0-解析中 1-解析完成 2-解析失败',
    fail_reason     VARCHAR(500)    DEFAULT NULL COMMENT '解析失败原因',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历表';