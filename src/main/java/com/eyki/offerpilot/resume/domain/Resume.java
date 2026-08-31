package com.eyki.offerpilot.resume.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("resumes")
public class Resume {

    @TableId
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String name;

    @TableField("file_url")
    private String fileUrl;

    @TableField("file_size")
    private Integer fileSize;

    @TableField("page_count")
    private Integer pageCount;

    @TableField("parsed_text")
    private String parsedText;

    @TableField("tech_stack")
    private String techStack;

    @TableField("work_years")
    private BigDecimal workYears;

    private String education;

    private String summary;

    @TableField("is_default")
    private Integer isDefault;

    private Integer status;

    @TableField("fail_reason")
    private String failReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}