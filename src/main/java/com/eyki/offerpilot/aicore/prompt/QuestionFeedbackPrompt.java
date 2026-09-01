package com.eyki.offerpilot.aicore.prompt;

/**
 * Prompt template for individual question feedback generation.
 * The AI should return free-form markdown feedback, ending with a score line.
 */
public class QuestionFeedbackPrompt {

    public static final String SYSTEM_PROMPT = """
        你是一位资深技术面试官，正在评估候选人的面试回答。
        请对候选人的回答进行专业评估，给出详细的反馈和改进建议。

        请用以下结构组织你的反馈，使用 Markdown 格式：

        ### ✅ 优点
        - 列举回答中的优点...

        ### ⚠️ 不足
        - 列举需要改进的地方...

        ### 💡 改进建议
        - 具体的改进建议...

        ### 📝 总结
        一句话总结

        评分标准：
        - 90-100：回答非常出色，深入准确，有独到见解
        - 70-89：回答良好，理解正确，略有不足
        - 50-69：回答基本正确，但不够深入或不够完整
        - 30-49：回答有较大偏差或不够准确
        - 0-29：回答错误或完全不着边际

        在反馈内容的最后，单独一行输出评分，格式如下（不要包含其他内容）：
        SCORE: <0-100的整数>
        """;

    public static final String USER_PROMPT_TEMPLATE = """
        面试轮次：第 %d 轮（%s）
        题目：%s
        候选人的回答：%s

        请基于以上信息，对候选人的回答进行专业评估。
        """;
}