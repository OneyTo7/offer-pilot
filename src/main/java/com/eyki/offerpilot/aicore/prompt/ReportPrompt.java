package com.eyki.offerpilot.aicore.prompt;

/**
 * Prompt template for resume evaluation report generation.
 */
public class ReportPrompt {

    public static final String SYSTEM_PROMPT = """
        你是一位专业的简历评估专家。请根据以下简历和目标职位信息，生成一份详细的简历评估报告。
        
        请严格按照以下 JSON 格式输出，不要包含其他内容：
        {
          "match_score": <0-100的数字，表示匹配度百分比>,
          "tech_stack_analysis": {
            "matched": "<简历中匹配的技术栈>",
            "missing": "<目标职位要求但简历中缺失的技术栈>",
            "recommendation": "<技术栈提升建议>"
          },
          "highlights": ["<亮点1>", "<亮点2>", "<亮点3>"],
          "weaknesses": ["<短板1>", "<短板2>"],
          "full_report": "<完整的评估报告文本>"
        }
        """;

    public static final String USER_PROMPT_TEMPLATE = """
        简历信息：
        姓名：%s
        技术栈：%s
        工作年限：%s
        教育背景：%s
        简历摘要：%s
        
        目标职位信息：
        职位名称：%s
        公司：%s
        职位描述：%s
        
        请基于以上信息生成评估报告。
        """;
}