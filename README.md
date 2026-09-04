<div align="center">

# 面壁 OfferPilot

**AI 驱动的简历智能分析与模拟面试平台**

[![CI](https://github.com/OneyTo7/offer-pilot/actions/workflows/ci.yml/badge.svg)](https://github.com/OneyTo7/offer-pilot/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)](https://spring.io/projects/spring-boot)

[🔗 在线体验](http://14.103.58.208) · [📖 文档](#) · [🐛 反馈](https://github.com/OneyTo7/offer-pilot/issues) · [⭐ Star](https://github.com/OneyTo7/offer-pilot)

</div>

---

## 🎯 项目简介

**面壁 (OfferPilot)** 面向技术研发岗位求职者——上传简历、设定目标职位，AI 即刻生成匹配度评估报告，并提供三轮模拟面试训练，帮助你在真正面试前查漏补缺。

> ✨ 完全开源（MIT 协议），前后端分离架构，支持自部署。

---

## 🚀 在线体验

访问 **[http://14.103.58.208](http://14.103.58.208)** 注册即可体验全部功能。

| 模式 | 说明 |
|------|------|
| 🆓 免费模式 | 使用平台默认 API Key，每月 10 万 Token 限额 |
| ⚡ Pro 模式 | 配置自己的 API Key（支持 DeepSeek/通义千问/GLM），无限制使用 |

---

## ✨ 核心功能

| 功能 | 说明 |
|------|------|
| 📄 **简历管理** | 上传 PDF 简历，AI 自动解析提取技术栈、项目经历、工作经历、教育背景。支持多简历管理 |
| 🎯 **目标职位** | 输入目标公司、职位名称及 JD 描述，一个简历可关联多个职位 |
| 📊 **评估报告** | AI 综合评估匹配度：技术栈对比分析、亮点提炼、短板提醒，异步生成 |
| 🎙️ **模拟面试** | 三面制 AI 面试（基础/深入/综合），每轮 10 题，SSE 流式实时反馈 + 评分，支持中断恢复 |
| 📚 **知识库（RAG）** | 上传 Markdown/TXT 文档，AI 自动分片向量化，面试时检索增强回答 |
| 🤖 **AI 小助手** | 通用 AI 对话入口，支持联网搜索（博查 API），RAG 检索知识库，类似 ChatGPT 的交互体验 |
| 🔑 **API Key 管理** | 支持配置自己的 API Key（DeepSeek/通义千问/GLM），用量透明展示（今日/月度/趋势） |

---

## 🏗️ 技术架构

```
┌──────────────┐     ┌─────────────────────────────────────────┐
│  Vue 3 前端   │     │           Spring Boot 4 后端             │
│              │     │                                         │
│  简历上传页    │────→│  ResumeController → PDFBox → MinIO      │
│  报告页       │     │              ↓                          │
│  面试对话页    │     │     ReportService → LLM → 报告          │
│              │     │              ↓                          │
│  EventSource  │     │       InterviewService                 │
│  SSE 接收器    │     │    ├─ AI 提问 + 反馈 (SSE 流式)         │
│              │     │    └─ 状态机管理 (3轮×10题)              │
└──────────────┘     └─────────────────────────────────────────┘
```

### 技术栈

| 层次 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.1.1 + Spring AI 2.0.1 |
| 运行环境 | JDK 21 + 虚拟线程 |
| 数据库 | PostgreSQL 16 + pgvector（向量库） |
| AI 模型 | DeepSeek / 通义千问 / GLM（OpenAI 兼容 API，用户可切换） |
| 文档解析 | PDFBox + Tess4j OCR + DeepSeek AI |
| 文件存储 | MinIO 对象存储 |
| 认证 | Sa-Token + JWT 双 Token（2h Access + 7d Refresh） |
| 流式输出 | SSE (Server-Sent Events) |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus |
| 向量嵌入 | BGE bge-small-zh-v1.5 (ONNX，本地运行) |
| 向量重排 | BGE bge-reranker-base (ONNX，交叉编码器) |

### Advisor Chain 管道

AI 调用链经过精心设计的管道，层层递进：

```
SafeValidAdvisor(0)           → 安全校验，过滤非法输入
TokenUsageAdvisor(0)          → Token 用量校验 + 累计
ReReadingAdvisor(1)           → Prompt 重读优化
MessageChatMemoryAdvisor(2)   → 对话记忆注入
RetrievalAugmentationAdvisor(3) → RAG 知识检索
MyLogAdvisor(4)               → 日志记录
ApiKeyRoutingAdvisor(5)       → 用户 Key / 平台 Key 路由（支持多模型服务商）
```

---

## 🚀 快速开始

### 前置条件

- JDK 21+
- Docker & Docker Compose
- Node.js 18+（前端开发）

### 本地启动

```bash
# 1. 克隆仓库
git clone https://github.com/OneyTo7/offer-pilot.git
cd offer-pilot

# 2. 启动基础设施（PostgreSQL + MinIO）
docker compose up -d

# 3. 启动后端（默认 dev 环境）
./mvnw spring-boot:run

# 4. 启动前端
cd offer-pilot-ui
npm install
npm run dev
```

访问 http://localhost:3000

### 生产部署

详见 [DEPLOY.md](DEPLOY.md)。

---

## 🔒 安全设计

| 维度 | 措施 |
|------|------|
| **数据隔离** | 所有数据按 `user_id` 严格隔离，每个查询带归属校验 |
| **API Key 加密** | AES-256-GCM 加密存储，内存中临时解密，用完即弃，不写日志 |
| **密码安全** | BCrypt 哈希存储，不可逆 |
| **认证** | 双 Token 机制（2h Access + 7d Refresh），支持自动刷新 |
| **传输安全** | 统一 `ApiResult<T>` 包装，异常不暴露内部细节 |

---

## 📁 项目结构

```
offer-pilot/
├── src/main/java/com/eyki/offerpilot/
│   ├── common/          # 公共：配置、异常、统一响应
│   ├── auth/            # 认证：注册/登录、JWT
│   ├── resume/          # 简历：上传、解析、存储
│   ├── position/        # 目标职位
│   ├── report/          # 评估报告：AI 生成
│   ├── interview/       # 模拟面试：状态机 + SSE
│   ├── aicore/          # AI 核心：Advisor Chain、RAG、记忆、Prompt
│   ├── knowledge/       # 知识库：文档管理、向量索引
│   └── storage/         # 文件存储：MinIO
├── src/main/resources/
│   ├── application.yaml          # 通用配置
│   ├── application-dev.yaml      # 开发环境
│   └── application-prod.yaml     # 生产环境
├── Dockerfile
├── docker-compose.yml
└── .env.example
```

---

## 🧪 测试

```bash
# 运行全部测试
./mvnw test -B

# 单个测试
./mvnw test -Dtest=OfferPilotApplicationTests
```

---

## 🤝 贡献

欢迎提交 Issue 和 PR！请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

### 联系作者

- 微信：Gonnatobeme
- [GitHub Issues](https://github.com/OneyTo7/offer-pilot/issues)

---

## 📄 许可证

[MIT License](LICENSE) — 可自由使用、修改、商用。

---

## ⭐ 致谢

- [Spring AI](https://docs.spring.io/spring-ai/reference/) — AI 应用框架
- [DeepSeek](https://platform.deepseek.com/) — 大语言模型（默认）
- [通义千问](https://dashscope.aliyun.com/) — 大语言模型（可选）
- [GLM (智谱)](https://open.bigmodel.cn/) — 大语言模型（可选）
- [pgvector](https://github.com/pgvector/pgvector) — 向量数据库
- [Sa-Token](https://sa-token.cc/) — 认证授权框架
- [Element Plus](https://element-plus.org/) — Vue 3 UI 组件库