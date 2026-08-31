package com.eyki.offerpilot.interview.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("interview_questions")
public class InterviewQuestion {

    @TableId
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    private Integer round;

    @TableField("question_index")
    private Integer questionIndex;

    @TableField("question_text")
    private String questionText;

    @TableField("user_answer")
    private String userAnswer;

    private String feedback;

    private BigDecimal score;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}