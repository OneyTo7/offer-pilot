CREATE TABLE users
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱，唯一',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密密码',
    nickname      VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    api_key       VARCHAR(255)          DEFAULT NULL COMMENT '用户自配的 DeepSeek API Key',
    status        TINYINT               DEFAULT 1 COMMENT '0-禁用 1-正常',
    last_login_at DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX         idx_email (email),
    INDEX         idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';