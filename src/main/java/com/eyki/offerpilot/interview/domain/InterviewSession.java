package com.eyki.offerpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.eyki.offerpilot.common.config.PgJsonbTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("interview_sessions")
public class InterviewSession {

    @TableId
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("resume_id")
    private Long resumeId;

    @TableField("position_id")
    private Long positionId;

    @TableField("current_round")
    private Integer currentRound;

    @TableField("current_question")
    private Integer currentQuestion;

    @TableField("total_questions")
    private Integer totalQuestions;

    @TableField(value = "score", typeHandler = PgJsonbTypeHandler.class)
    private String score;

    private String summary;

    @TableField("duration_seconds")
    private Integer durationSeconds;

    private Integer status;

    @TableField("expired_at")
    private LocalDateTime expiredAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Integer version;
}