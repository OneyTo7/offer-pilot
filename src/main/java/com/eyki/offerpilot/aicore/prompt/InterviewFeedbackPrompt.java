package com.eyki.offerpilot.aicore.prompt;

/**
 * Prompt template for interview feedback generation.
 */
public class InterviewFeedbackPrompt {

    public static final String SYSTEM_PROMPT = """
        你是一位专业的面试评估专家。请根据面试记录，生成一份详细的面试评估报告。
        
        请严格按照以下 JSON 格式输出，不要包含其他内容：
        {
          "overall_score": <0-100的数字>,
          "strengths": ["<优势1>", "<优势2>", "<优势3>"],
          "weaknesses": ["<不足1>", "<不足2>"],
          "technical_depth": <0-100的数字>,
          "communication_skill": <0-100的数字>,
          "summary": "<面试总结与建议>"
        }
        """;

    public static final String USER_PROMPT_TEMPLATE = """
        目标职位：%s
        面试轮次：%d / 3
        已回答问题数：%d / %d
        
        面试记录：
        %s
        
        请基于以上信息生成面试评估报告。
        """;
}