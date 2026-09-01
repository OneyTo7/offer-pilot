package com.eyki.offerpilot.aicore.prompt;

/**
 * 简历解析 Prompt。
 *
 * <p>指导 DeepSeek 从原始文本中提取结构化简历信息。
 * JSON 输出格式由 {@code BeanOutputConverter} 自动生成并拼接，不再在此处维护手动 JSON Schema。</p>
 */
public class ResumeParsePrompt {

    private static final String SYSTEM_PROMPT = """
        你是一个专业的简历解析助手。你的任务是从用户提供的简历文本中提取结构化信息。

        要求：
        1. 严格按 JSON 格式返回，不要添加任何额外说明文字
        2. 无法提取的字段设为 null，不要编造
        3. 工作年限（workYears）以年为单位，保留一位小数
        4. 时间格式统一为 "YYYY-MM" 或 "YYYY-MM-DD"
        5. 技能标签按类别分组，如 "后端开发：Java、Spring Boot"
        """;

    private static final String USER_PROMPT_TEMPLATE = """
        请从以下简历文本中提取结构化信息。

        ===== 简历文本 =====
        %s
        ===== 结束 =====
        """;

    public static String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String buildUserPrompt(String rawText) {
        return String.format(USER_PROMPT_TEMPLATE, rawText);
    }
}