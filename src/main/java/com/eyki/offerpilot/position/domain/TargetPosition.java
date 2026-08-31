package com.eyki.offerpilot.position.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("target_positions")
public class TargetPosition {

    @TableId
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("resume_id")
    private Long resumeId;

    private String title;

    private String company;

    @TableField("jd_text")
    private String jdText;

    private String location;

    @TableField("salary_range")
    private String salaryRange;

    @TableField("is_default")
    private Integer isDefault;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}