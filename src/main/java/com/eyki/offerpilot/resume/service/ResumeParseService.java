package com.eyki.offerpilot.resume.service;

import com.eyki.offerpilot.resume.domain.Resume;

/**
 * 简历解析服务。
 *
 * 管线：PDFBox 提取文本 → Tess4j OCR 降级 → DeepSeek AI 结构化解析。
 */
public interface ResumeParseService {

    /**
     * 解析简历文件，提取结构化信息并回写到 Resume 实体。
     *
     * @param resume 已保存到数据库的 Resume 实体（含 fileUrl）
     * @param apiKey 用户的自定义 API Key（可选，用于绕过免费额度限制）
     * @param apiBaseUrl 用户的自定义 API Base URL（可选）
     * @param apiModel 用户的自定义模型名称（可选）
     * @return 更新后的 Resume（含 parsedText、JSON 字段、status）
     */
    Resume parse(Resume resume, String apiKey, String apiBaseUrl, String apiModel);
}