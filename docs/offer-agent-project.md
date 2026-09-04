---
name: offer-agent-project
description: 面壁(OfferPilot) AI简历智能平台项目定义
metadata:
  type: project
---

# 面壁（OfferPilot）

AI 简历智能平台，面向技术研发岗位求职者。产品名"面壁"，寓意"面试前的苦练"。

**核心功能：** 上传简历 → AI 诊断 + 模拟面试三轮（每轮 10 题）

**技术栈：** Spring Boot 4.1.1 + Spring AI 2.0.1 + DeepSeek/通义千问/GLM + pgvector + Vue3 + Element Plus

**关键决策：**

- 文件存储用 MinIO，不用 OSS
- 注册用邮箱+密码，不用短信验证码
- 面试三面制（一面基础/二面深入/三面综合），中断 1 小时内可恢复
- 评估报告用 JSON 结构化输出，只做诊断不做优化建议
- RAG 检索按 user_id 隔离
- 限流：免费层每天3次报告+1次面试，用户可自配 API Key 解锁不限量
- 部署用 Docker Compose，服务器火山引擎 2C4G

**文档：** [[PRD-AI简历智能平台]] [[offer-agent-architecture]]