CREATE TABLE interview_questions
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id     BIGINT   NOT NULL COMMENT '所属面试会话',
    round          TINYINT  NOT NULL COMMENT '轮次：1-一面 2-二面 3-三面',
    question_index INT      NOT NULL COMMENT '题号 1-10',
    question_text  TEXT     NOT NULL COMMENT 'AI 提出的问题',
    user_answer    LONGTEXT          DEFAULT NULL COMMENT '用户回答',
    feedback       LONGTEXT          DEFAULT NULL COMMENT 'AI 反馈',
    score          DECIMAL(3, 1)     DEFAULT NULL COMMENT '本题评分 1-10',
    status         TINYINT           DEFAULT 0 COMMENT '0-待答 1-已答 2-已跳过',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX          idx_session_id (session_id),
    INDEX          idx_round (round),
    FOREIGN KEY (session_id) REFERENCES interview_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试题目表';