CREATE TABLE resumes
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL COMMENT '所属用户',
    name            VARCHAR(100) NOT NULL COMMENT '简历文件名',
    file_url        VARCHAR(500)          DEFAULT NULL COMMENT 'MinIO 文件路径',
    file_size       INT                   DEFAULT 0 COMMENT '文件大小(字节)',
    page_count      INT                   DEFAULT 0 COMMENT 'PDF 页数',

    -- 原始文本（PDFBox / Tess4j 提取）
    parsed_text     LONGTEXT              DEFAULT NULL COMMENT '全文原始文本',

    -- 结构化字段（DeepSeek AI 解析产出，JSON 格式）
    basic_info      JSON                  DEFAULT NULL COMMENT '基本信息（姓名、手机、邮箱、工作年限等）',
    education       JSON                  DEFAULT NULL COMMENT '教育经历（数组：[学校、专业、学位、时间]）',
    work_experience JSON                  DEFAULT NULL COMMENT '工作经历（数组：[公司、职位、时间、职责]）',
    projects        JSON                  DEFAULT NULL COMMENT '项目经历（数组：[项目名、角色、时间、描述]）',
    skills          JSON                  DEFAULT NULL COMMENT '技能标签（数组：[分类、技能列表]）',
    certificates    JSON                  DEFAULT NULL COMMENT '证书/语言（数组：[名称、时间、颁发机构]）',

    -- 摘要 & 调试
    summary         TEXT                  DEFAULT NULL COMMENT 'AI 生成的简历摘要',
    raw_response    LONGTEXT              DEFAULT NULL COMMENT 'AI 解析原始返回（调试用，可能含 markdown 代码块）',

    -- 状态 & 标记
    is_default      TINYINT               DEFAULT 0 COMMENT '0-否 1-默认简历',
    status          TINYINT               DEFAULT 0 COMMENT '0-解析中 1-解析完成 2-解析失败',
    fail_reason     VARCHAR(500)          DEFAULT NULL COMMENT '解析失败原因',

    -- 时间戳
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX           idx_user_id (user_id),
    INDEX           idx_status (status),
    FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历表';