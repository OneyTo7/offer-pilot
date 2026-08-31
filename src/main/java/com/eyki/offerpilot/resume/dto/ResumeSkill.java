package com.eyki.offerpilot.resume.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 技能分类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeSkill {

    private String category;
    private List<String> skills;
}