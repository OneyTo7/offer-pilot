CREATE TABLE target_positions
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL COMMENT '所属用户',
    resume_id    BIGINT       NOT NULL COMMENT '关联简历',
    title        VARCHAR(200) NOT NULL COMMENT '职位名称，如 Java高级开发工程师',
    company      VARCHAR(200)          DEFAULT NULL COMMENT '公司名称',
    jd_text      TEXT         NOT NULL COMMENT '职位描述全文',
    location     VARCHAR(100)          DEFAULT NULL COMMENT '工作地点',
    salary_range VARCHAR(50)           DEFAULT NULL COMMENT '薪资范围',
    is_default   TINYINT               DEFAULT 0 COMMENT '0-否 1-默认职位',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX        idx_user_id (user_id),
    INDEX        idx_resume_id (resume_id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (resume_id) REFERENCES resumes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标职位表';