# 面壁 OfferPilot

> 让每一次面试都更有准备 —— AI 驱动的简历智能分析与面试模拟平台

**面壁 (OfferPilot)** 是一款面向技术研发岗位求职者的 AI 简历智能平台。上传简历、设定目标职位，AI 即刻生成匹配度评估报告，并提供模拟面试训练，帮助你在真正面试前查漏补缺。

---

## ✨ 功能

### 📄 简历管理
- 上传 PDF 简历，自动解析提取结构化信息（PDFBox + AI）
- 技术栈、项目经历、工作经历、教育背景一目了然
- 多简历管理，支持设为默认

### 🎯 目标职位
- 自由输入目标职位名称 + 公司 + JD
- 一个简历可关联多个职位

### 📊 评估报告
- AI 综合评估简历与目标职位的匹配度
- 技术栈对比分析（匹配/缺失/建议）
- 亮点提炼 + 短板提醒
- 异步生成，生成后自动通知

### 🎙️ 模拟面试（三面制）
- **技术一面**：基础技术能力（语言、框架、项目基础）
- **技术二面**：深入技术能力（底层原理、性能优化）
- **技术三面**：综合技术能力（架构设计、开放题）
- SSE 流式输出，AI 逐题反馈 + 评分
- 中断恢复：1 小时内可继续
- 面试总结报告：三轮汇总 + 综合评分

### 🔑 API Key 管理
- **免费层**：使用平台 Key，每日限额（3 份报告 + 1 次面试）
- **Pro 层**：自配 DeepSeek API Key，无限使用

---

## 🏗️ 架构

```
┌──────────────┐     ┌─────────────────────────────────┐
│  Vue 3 前端   │     │       Spring Boot 4 后端          │
│              │     │                                 │
│  简历上传页    │────→│  ResumeController                │
│  报告页       │     │    ↓                            │
│  面试对话页    │     │  ResumeService → PDFBox → MinIO │
│              │     │    ↓                            │
│  EventSource  │     │  ReportService → LLM → 报告     │
│  SSE 接收器    │     │    ↓                            │
│              │     │  InterviewService                │
│              │     │    ├─ AI 提问 + 反馈              │
│              │     │    └─ SSE 流式输出                │
└──────────────┘     └─────────────────────────────────┘
```

### 模块结构

```
com.eyki.offerpilot/
├── common/          # 公共：配置、异常、统一响应体、工具类
├── auth/            # 认证：注册/登录、Sa-Token + JWT
├── resume/          # 简历：上传、PDFBox 解析、结构化存储
├── position/        # 目标职位：CRUD
├── report/          # 评估报告：AI 生成、历史查看
├── interview/       # 模拟面试：三面制、SSE 流式、状态机
├── aicore/          # AI 核心：Advisor Chain、RAG、记忆、Prompt
│   ├── advisor/     # SafeValidAdvisor、ReReadingAdvisor
│   ├── rag/         # RagService、TokenTextSplitter
│   ├── memory/      # 对话记忆
│   ├── prompt/      # 报告/面试 Prompt 模板
│   └── service/     # AiService（LLM 调用封装）
├── knowledge/       # 知识库
└── storage/         # 文件存储：MinIO
```

### 技术栈

| 层次 | 技术 | 说明 |
|------|------|------|
| 后端框架 | Spring Boot 4.1.1 + Spring AI 2.0.1 | |
| 运行环境 | JDK 21 | 预览特性已启用 |
| 数据库 | MySQL 8（业务数据）+ PostgreSQL + pgvector（向量库） | |
| AI 模型 | DeepSeek（OpenAI 兼容 API） | |
| 文档解析 | PDFBox + Tess4j OCR | |
| 文件存储 | MinIO 对象存储 | |
| 认证 | Sa-Token + JWT 双 Token | 2h Access + 7d Refresh |
| 流式输出 | SSE (Server-Sent Events) | |
| 构建 | Maven (wrapped) | |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus | 独立仓库 |

---

## 🚀 快速开始

### 前置条件

- JDK 21+
- Docker & Docker Compose
- Node.js 18+（前端开发）

### 1. 启动基础设施

```bash
docker compose up -d
```

启动 MySQL 8、PostgreSQL 16 (pgvector)、MinIO、Redis。

### 2. 启动后端

```bash
# 默认激活 dev 环境，连接本地 Docker Compose 服务
./mvnw spring-boot:run
```

### 3. 启动前端

```bash
cd offer-pilot-ui
npm install
npm run dev
```

访问 http://localhost:3000

### 4. 配置 DeepSeek API Key

启动后访问 **个人设置 → API Key 配置**，填入你的 DeepSeek API Key 解锁全部功能。

> 未配置 API Key 时使用平台默认 Key，每天限 3 份报告 + 1 次面试。

---

## 📸 截图

> 待添加

---

## 📁 项目结构

```
offer-pilot/
├── src/main/java/          # 后端源码
│   └── com/eyki/offerpilot/
├── src/main/resources/     # 配置文件
│   ├── application.yaml          # 通用配置
│   ├── application-dev.yaml      # 开发环境
│   ├── application-prod.yaml     # 生产环境
│   └── db/changelog/             # Liquibase 迁移
├── Dockerfile               # 多阶段构建
├── docker-compose.yml       # 服务编排
├── .env.example             # 环境变量示例
└── docs/                    # 文档
    ├── prd-offer-pilot.md          # 产品需求文档
    ├── offer-agent-architecture.md # 架构设计文档
    └── offer-agent-project.md      # 项目定义文档

offer-pilot-ui/             # 前端项目（独立仓库）
├── src/
│   ├── api/                # API 请求
│   ├── components/          # 公共组件
│   ├── router/              # 路由
│   ├── stores/              # 状态管理
│   ├── views/               # 页面
│   └── types/               # 类型定义
└── .env.example             # 前端环境变量示例
```

---

## 📡 API 概览

所有接口以 `/api/v1` 为前缀，统一返回 `ApiResult<T>` 格式。

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| Auth | POST | `/auth/register` | 注册 |
| Auth | POST | `/auth/login` | 登录 |
| Auth | POST | `/auth/refresh` | 刷新 Token |
| Auth | POST | `/auth/logout` | 登出 |
| Auth | GET | `/auth/me` | 当前用户信息 |
| Auth | PUT | `/auth/profile` | 更新个人信息 |
| Auth | PUT | `/auth/api-key` | 更新 API Key |
| Auth | DELETE | `/auth/api-key` | 清除 API Key |
| Resume | POST | `/resumes` | 上传简历 |
| Resume | GET | `/resumes` | 简历列表 |
| Resume | GET | `/resumes/{id}` | 简历详情 |
| Resume | DELETE | `/resumes/{id}` | 删除简历 |
| Resume | PUT | `/resumes/{id}/default` | 设为默认简历 |
| Report | POST | `/reports` | 生成评估报告 |
| Report | GET | `/reports` | 报告列表 |
| Report | GET | `/reports/{id}` | 报告详情 |
| Report | DELETE | `/reports/{id}` | 删除报告 |
| Interview | POST | `/interviews` | 创建面试会话 |
| Interview | GET | `/interviews` | 面试列表 |
| Interview | GET | `/interviews/{id}` | 面试详情 |
| Interview | POST | `/interviews/{id}/start-round` | 开始轮次 |
| Interview | POST | `/interviews/{id}/answer` | 回答问题（SSE） |
| Interview | POST | `/interviews/{id}/skip` | 跳过题目 |
| Interview | POST | `/interviews/{id}/end` | 结束面试 |
| Interview | GET | `/interviews/{id}/summary` | 面试总结 |
| Health | GET | `/api/health` | 健康检查 |

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

欢迎贡献代码！请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解开发流程和规范。

---

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

---

## 🙏 致谢

- [Spring AI](https://docs.spring.io/spring-ai/reference/) — AI 应用框架
- [DeepSeek](https://platform.deepseek.com/) — 大语言模型
- [pgvector](https://github.com/pgvector/pgvector) — 向量数据库
- [Sa-Token](https://sa-token.cc/) — 认证授权框架
- [Element Plus](https://element-plus.org/) — Vue 3 UI 组件库