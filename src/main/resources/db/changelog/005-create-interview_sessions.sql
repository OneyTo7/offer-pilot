--changeset init:005
CREATE TABLE interview_sessions
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT   NOT NULL,
    resume_id        BIGINT   NOT NULL,
    position_id      BIGINT   NOT NULL,
    current_round    TINYINT           DEFAULT 1 COMMENT '当前轮次：1-一面 2-二面 3-三面',
    current_question INT               DEFAULT 0 COMMENT '当前题目序号 0-10',
    total_questions  INT               DEFAULT 0 COMMENT '总回答题数',
    score            JSON              DEFAULT NULL COMMENT '各维度评分 JSON',
    summary          TEXT              DEFAULT NULL COMMENT '面试总结文本',
    duration_seconds INT               DEFAULT 0 COMMENT '面试时长(秒)',
    status           TINYINT           DEFAULT 0 COMMENT '0-进行中 1-已完成 2-已过期 3-已中断',
    expired_at       DATETIME          DEFAULT NULL COMMENT '过期时间(中断后+1小时)',
    started_at       DATETIME          DEFAULT NULL COMMENT '面试开始时间',
    finished_at      DATETIME          DEFAULT NULL COMMENT '面试结束时间',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX            idx_user_id (user_id),
    INDEX            idx_status (status),
    INDEX            idx_expired_at (expired_at),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (resume_id) REFERENCES resumes (id),
    FOREIGN KEY (position_id) REFERENCES target_positions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试会话表';