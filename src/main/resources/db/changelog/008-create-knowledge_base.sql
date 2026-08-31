-- liquibase formatted sql

-- changeset offerpilot:008-knowledge_base
-- comment: 创建知识库文档表，每个文档对应一次上传（文本或文件），内容分片后索引到 pgvector

CREATE TABLE knowledge_base
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id      BIGINT       NOT NULL COMMENT '用户 ID',
    title        VARCHAR(255) NOT NULL COMMENT '文档标题',
    content      LONGTEXT COMMENT '原始文本内容（文件上传时存储解析后的文本）',
    content_type VARCHAR(20)  NOT NULL DEFAULT 'text' COMMENT '内容类型：text/file/url',
    file_url     VARCHAR(500)          DEFAULT NULL COMMENT '文件存储 URL（MinIO）',
    chunk_count  INT                   DEFAULT 0 COMMENT '分片数量',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=索引中 1=已完成 2=失败',
    fail_reason  VARCHAR(500)          DEFAULT NULL COMMENT '失败原因',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX        idx_user_id (user_id),
    INDEX        idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';