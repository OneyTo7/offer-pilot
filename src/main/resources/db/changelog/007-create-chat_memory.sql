--changeset init:007
CREATE TABLE chat_memory (
    id               BIGINT           AUTO_INCREMENT PRIMARY KEY,
    conversation_id  VARCHAR(100)     NOT NULL COMMENT '会话 ID（interview session ID）',
    user_id          BIGINT           DEFAULT NULL COMMENT '用户 ID，便于按用户查询对话记录',
    message_type     VARCHAR(20)      NOT NULL COMMENT '消息类型：USER/ASSISTANT/SYSTEM',
    content          LONGTEXT         NOT NULL COMMENT '消息内容',
    metadata         JSON             DEFAULT NULL COMMENT '消息元数据',
    created_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对话记忆表（每条消息一行）';