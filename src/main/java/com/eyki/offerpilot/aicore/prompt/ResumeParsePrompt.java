package com.eyki.offerpilot.aicore.prompt;

/**
 * 简历解析 Prompt。
 *
 * 指导 DeepSeek 从原始文本中提取结构化简历信息，严格按 JSON Schema 返回。
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
        请从以下简历文本中提取结构化信息，严格按 JSON 格式返回，不要包含 markdown 代码块标记。
        
        ===== 简历文本 =====
        %s
        ===== 结束 =====
        
        返回的 JSON 结构如下（请严格遵循此结构，不要添加额外字段）：
        {
          "basic_info": {
            "name": "姓名",
            "gender": "性别",
            "birthDate": "出生日期",
            "phone": "手机号",
            "email": "电子邮箱",
            "location": "现居住地",
            "expectedPosition": "期望职位",
            "expectedSalary": "期望薪资",
            "workYears": 工作年限（数字）,
            "highestDegree": "最高学历",
            "politicalStatus": "政治面貌",
            "currentCompany": "当前公司",
            "currentPosition": "当前职位"
          },
          "education": [
            {
              "school": "学校名称",
              "major": "专业",
              "degree": "学历/学位",
              "startDate": "开始时间",
              "endDate": "结束时间",
              "isFullTime": true/false,
              "description": "描述"
            }
          ],
          "work_experience": [
            {
              "company": "公司名称",
              "position": "职位",
              "department": "部门",
              "startDate": "开始时间",
              "endDate": "结束时间",
              "isCurrent": true/false,
              "responsibilities": "工作职责描述",
              "achievements": ["成就1", "成就2"],
              "technologies": ["技术1", "技术2"]
            }
          ],
          "projects": [
            {
              "name": "项目名称",
              "role": "担任角色",
              "startDate": "开始时间",
              "endDate": "结束时间",
              "description": "项目描述",
              "responsibilities": "职责",
              "technologies": ["技术1", "技术2"],
              "highlights": "亮点"
            }
          ],
          "skills": [
            {
              "category": "分类名称",
              "skills": ["技能1", "技能2"]
            }
          ],
          "certificates": [
            {
              "name": "证书名称",
              "date": "获得时间",
              "issuer": "颁发机构",
              "type": "certificate 或 language",
              "level": "等级（如 CET-6）"
            }
          ],
          "summary": "简历摘要（2-3句话概括候选人的核心竞争力和特点）"
        }
        """;

    public static String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String buildUserPrompt(String rawText) {
        return String.format(USER_PROMPT_TEMPLATE, rawText);
    }
}