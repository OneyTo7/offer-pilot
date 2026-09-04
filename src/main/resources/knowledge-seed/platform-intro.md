# 面壁 OfferPilot - AI 简历智能平台

## 平台简介

面壁（OfferPilot）是一个面向技术求职者的 AI 简历智能平台。用户上传简历、设定目标职位后，平台通过 AI 自动生成匹配度评估报告，并提供三轮模拟面试（每轮 10 题）的完整面试体验。

## 主要功能

### 1. 简历解析
- 支持 PDF 简历上传，通过阿里云百炼文档解析服务提取结构化信息
- 自动提取技术栈、工作年限、教育背景等关键字段
- 支持多份简历管理，可设置默认简历

### 2. 评估报告
- 基于简历与目标职位的匹配度 AI 分析
- 生成技术栈匹配分析、亮点与不足、学习建议
- 支持免费版（每日 3 次报告额度）

### 3. 模拟面试
- 三轮模拟面试（每轮 10 题），覆盖不同难度级别
- SSE 流式输出 AI 回答反馈，支持打字机效果
- 问题自动评分与反馈建议
- 支持联网搜索工具调用（WebSearchTool）

### 4. 知识库
- 支持 Markdown 和纯文本文件上传
- 自动分片、向量化索引到 pgvector
- 语义搜索用户知识库（RAG 检索）
- 用户级与系统级知识库分类

### 5. AI 小助手
- 通用 AI 对话入口，类似 ChatGPT 的交互体验
- 支持联网搜索（通过 WebSearchTool 调用博查 API）
- 对话历史管理与多会话切换
- 每次对话独立记忆，支持上下文关联

### 6. API Key 管理
- 用户可自行配置 API Key（支持 DeepSeek / 通义千问 / GLM 等）
- 实时查看 Token 用量
- API Key 通过 AES-256-GCM 加密存储

## 技术架构

### 后端技术栈
- **框架**: Spring Boot 4.1.1 + Spring AI 2.0.1
- **语言**: Java 21（虚拟线程）
- **构建工具**: Maven
- **LLM**: DeepSeek（默认）/ 通义千问 / GLM（兼容 OpenAI API，用户可切换）
- **向量数据库**: PostgreSQL + pgvector（512 维，BGE bge-small-zh-v1.5 模型）
- **认证授权**: Sa-Token + JWT（双 Token：2 小时 access + 7 天 refresh）
- **文件存储**: MinIO（对象存储）
- **文档解析**: 阿里云百炼
- **数据库迁移**: Liquibase

### 前端技术栈
- **框架**: Vue 3 + TypeScript
- **构建工具**: Vite
- **UI 组件库**: Element Plus
- **SSE 流式**: fetch + ReadableStream 手动解析

### AI 架构
- **Advisor Chain**: SafeValid(0) → TokenUsage(0) → ReReading(1) → MessageChatMemory(2) → RetrievalAugmentation(3) → MyLog(4) → ApiKeyRouting(5)
- **RAG 检索**: 用户级数据隔离，所有向量条目标记 user_id
- **记忆系统**: PgChatMemory（PostgreSQL 存储对话历史）
- **Token 用量追踪**: 每日追踪，免费版上限 10 万 Token/月

## 开源信息

- **项目地址**: GitHub（即将开源）
- **开源协议**: MIT
- **作者**: OneyTo7
- **服务器**: 火山引擎 2C4G
- **数据库**: PostgreSQL 16 + pgvector
- **部署方式**: 宝塔面板（Java 项目管理器 + Nginx）

## 数据安全

- 所有用户数据严格隔离，查询按 user_id 过滤
- 密码通过 BCrypt 哈希存储
- API Key 通过 AES-256-GCM 加密
- 免费版 Token 额度限制：每日 3 次报告 + 1 次面试 + 10 万 Token/月