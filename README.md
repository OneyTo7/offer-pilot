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

### 🤖 AI 大模型配置
- 大模型配置页面（`/ai`）集中管理 AI 相关设置
- 支持配置自己的 DeepSeek API Key，解锁无限使用
- 用量透明展示：今日用量、月度统计、每日趋势
- API Key 加密存储，保障账户安全

### 🔑 使用模式
- **免费模式**：使用平台默认 Key，每月 10 万 Token 限额
- **Pro 模式**：自配 DeepSeek API Key，无限制使用

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
| 数据库 | PostgreSQL + pgvector（向量库） | |
| AI 模型 | DeepSeek（OpenAI 兼容 API） | |
| 文档解析 | 阿里云百炼（文档智能解析） | |
| 文件存储 | MinIO 对象存储 | |
| 认证 | Sa-Token + JWT 双 Token | 2h Access + 7d Refresh |
| 流式输出 | SSE (Server-Sent Events) | |
| 构建 | Maven (wrapped) | |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus | 独立仓库 `offer-pilot-ui` |
| 向量数据库 | pgvector | PostgreSQL 16 扩展，512 维 |
| 向量嵌入 | BGE bge-small-zh-v1.5 (ONNX) | 本地模型，无需 API Key |
| 向量重排 | BGE bge-reranker-base (ONNX) | 本地模型，交叉编码器 |

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

启动 PostgreSQL 16 (pgvector)、MinIO。

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

> 未配置 API Key 时使用平台默认 Key，每月限 10 万 Token。用量可在「大模型配置」页面查看。

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
| Knowledge | POST | `/knowledge` | 创建知识文档（文本） |
| Knowledge | POST | `/knowledge/upload` | 上传知识库文件（Markdown/TXT） |
| Knowledge | GET | `/knowledge` | 知识文档列表 |
| Knowledge | GET | `/knowledge/{id}` | 知识文档详情 |
| Knowledge | GET | `/knowledge/{id}/chunks` | 查看文档分片 |
| Knowledge | DELETE | `/knowledge/{id}` | 删除文档 |
| Knowledge | POST | `/knowledge/search` | 语义搜索知识库 |
| File | GET | `/files/{*filePath}` | 文件下载（MinIO 代理） |
| AI | GET | `/ai/info` | 大模型配置信息（API Key 状态、用量、套餐、社区） |
| Health | GET | `/api/health` | 健康检查 |

---

## 🔒 安全与隐私

### 数据隔离

所有用户数据严格按 `user_id` 隔离：

| 数据 | 隔离方式 |
|------|---------|
| 简历 | 所有查询带 `WHERE user_id = ?`，MyBatis-Plus LambdaQueryWrapper 约束 |
| 目标职位 | 同上，增删改查均校验当前用户归属 |
| 评估报告 | 每个报告关联 `user_id`，controller 层查询前校验归属 |
| 面试会话 | 同上，`getSession`、`startRound`、`answer` 等接口均校验归属 |
| 知识库文档 | 按 `user_id` 过滤，RAG 检索时通过 `vector_store_filter_expression` 注入过滤条件 |
| 向量存储 | pgvector 每条记录带 `user_id` 标签，检索时 Spring AI 的 `RetrievalAugmentationAdvisor` 自动注入过滤表达式 |

**原则**：后端 Service 层每个读取操作都校验 `user_id` 匹配，防止越权访问。不存在"所有用户共享"的数据接口。

### API Key 加密存储

用户配置的 DeepSeek API Key 采用 **AES-256-GCM** 加密后存入数据库：

```
保存流程：明文 Key → AES-256-GCM 加密 → Base64 编码 → 存入 DB
读取流程：DB 密文 → Base64 解码 → AES-256-GCM 解密 → 明文 Key（仅内存中短暂存在）
```

- 加密密钥通过环境变量 `API_KEY_ENCRYPTION_SECRET` 注入，**不写死在代码或配置文件中**
- 每个请求处理时临时解密，使用后即被 GC 回收，**不会写入日志**
- 前端响应中 **不返回真实 Key**，仅通过 `has_api_key` 布尔值告知是否已配置
- 数据库泄露时攻击者拿到的只是密文，无加密密钥无法解密

> ⚠️ 如果是从旧版本升级的用户，此前已明文存储的 API Key 不会自动迁移。如需加密旧数据，可通过设置页面清除后重新配置。

### Token 用量透明

系统对每次 LLM 调用的 token 消耗进行记录，记入 `user_token_usage` 表：

- 记录维度：`user_id` + `usage_date` + `prompt_tokens` + `completion_tokens`
- 用户可在大模型配置页面查看 **今日用量**、**本月累计**、**每日趋势**
- 免费用户每月 10 万 tokens 上限，用完后接口返回 429
- 配置了自己 API Key 的用户不限量，用量仅作展示

### 传输安全

- 所有 API 响应通过统一的 `ApiResult<T>` 包装，异常信息不会暴露内部实现细节
- 敏感信息（API Key）在 JSON 序列化时标记为 `@JsonIgnore`
- 前后端通信建议在生产环境启用 HTTPS（需自行配置反向代理）

### 认证安全

- 密码采用 **BCrypt** 哈希存储（`hutool-crypto` 实现）
- 双 Token 机制：2 小时 Access Token + 7 天 Refresh Token
- Refresh Token 一次有效，刷新后旧 Token 自动失效
- 支持 Token 自动刷新（前端 Axios 拦截器 + 订阅队列模式）

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