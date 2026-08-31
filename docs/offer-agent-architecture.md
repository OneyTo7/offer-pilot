# 面壁（OfferPilot）技术架构设计文档

> 版本：v1.0
> 日期：2026-08-31
> 状态：定稿
> 对应 PRD：PRD-AI简历智能平台.md

---

## 1. 项目概述

AI 简历智能平台，面向技术研发岗位求职者，提供简历评估报告 + AI 模拟面试服务。

### 1.1 项目信息

| 项目 | 值 |
|------|-----|
| 项目名 | 面壁（OfferPilot） |
| 包名 | com.eyki.offeragent |
| 语言 | Java 21 |
| 框架 | Spring Boot 4.1.1 + Spring AI 2.0.1 |
| LLM | DeepSeek（OpenAI 兼容 API） |
| 向量模型 | BGE bge-small-zh-v1.5（512维） |
| 构建工具 | Maven |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus |

### 1.2 MVP 功能范围

```
邮箱密码注册/登录
    ↓
上传简历(PDF) → 百炼文档解析 → 结构化存储
    ↓
手动填写目标职位
    ↓
生成评估报告(匹配度+技术栈+亮点+短板) → JSON结构化
    ↓
模拟面试(三面制: 一面基础/二面深入/三面综合, 每面10题)
    ↓
面试总结报告(三轮汇总, 评分+建议)
```

---

## 2. 技术栈详情

### 2.1 后端

| 组件 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 21 | |
| 框架 | Spring Boot | 4.1.1 | |
| AI 框架 | Spring AI | 2.0.1 | ChatClient、Advisor、RAG |
| 数据库 | MySQL | 8.0 | 业务数据 |
| 向量库 | PostgreSQL + pgvector | 16 | 简历/职位知识库 |
| 缓存 | Redis | 7 | 可选，后续优化用 |
| 对象存储 | MinIO | latest | 简历文件存储 |
| 认证 | Sa-Token + JWT | 最新 | 登录鉴权 |
| 数据库迁移 | Liquibase | 内置 | DDL 版本管理 |
| LLM | DeepSeek | API | 对话生成 |
| 文档解析 | 阿里云百炼 | API | PDF 文本提取 |
| 日志 | Logback + SLF4j | 内置 | |
| 工具 | Lombok | 最新 | 减少样板代码 |

### 2.2 前端

| 组件 | 技术 | 用途 |
|------|------|------|
| 框架 | Vue 3 | |
| 语言 | TypeScript | |
| 构建 | Vite | |
| UI 组件库 | Element Plus | 表单/表格/弹窗/上传 |
| 状态管理 | Pinia | |
| 路由 | Vue Router | |
| HTTP | Axios | 调用后端 API |
| SSE | EventSource | 流式接收面试对话 |

### 2.3 部署

| 组件 | 技术 | 用途 |
|------|------|------|
| 服务器 | 火山引擎 2C4G (已有) | 运行所有服务 |
| 容器化 | Docker + Docker Compose | 服务编排 |
| 对象存储 | MinIO 容器 | 简历文件 |
| 数据库 | MySQL 8 容器 | 业务数据 |
| 向量库 | pgvector 容器 | 向量数据 |
| 反向代理 | Nginx 容器 | 前端静态文件 + API 代理 |
| CI/CD | GitHub Actions | 自动构建部署 |

---

## 3. 项目结构

### 3.1 后端模块结构

```
offer-agent/
├── pom.xml                          # 父 POM
├── src/main/java/com/eyki/offeragent/
│   ├── OfferAgentApplication.java   # 启动类
│   │
│   ├── common/                      # 公共模块
│   │   ├── config/                  # 全局配置
│   │   │   ├── JacksonConfig.java   # JSON 序列化配置
│   │   │   └── CorsConfig.java      # 跨域配置
│   │   ├── constant/                # 常量
│   │   │   └── ApiConstant.java     # API 相关常量
│   │   ├── exception/               # 异常体系
│   │   │   ├── BusinessException.java       # 业务异常
│   │   │   ├── UnauthorizedException.java   # 未授权
│   │   │   └── GlobalExceptionHandler.java  # 全局异常处理
│   │   ├── model/                   # 统一响应体
│   │   │   ├── ApiResult.java       # 统一返回
│   │   │   └── PageResult.java      # 分页返回
│   │   └── util/                    # 工具类
│   │       ├── TraceIdUtil.java     # 链路追踪 ID
│   │       └── JsonUtil.java        # JSON 工具
│   │
│   ├── auth/                        # 认证模块
│   │   ├── controller/
│   │   │   └── AuthController.java
│   │   ├── domain/
│   │   │   └── User.java            # 用户实体
│   │   ├── dto/
│   │   │   ├── LoginRequest.java
│   │   │   ├── RegisterRequest.java
│   │   │   ├── TokenResponse.java
│   │   │   └── UserVO.java
│   │   ├── repository/
│   │   │   └── UserRepository.java  # MyBatis-Plus Mapper
│   │   ├── service/
│   │   │   ├── AuthService.java
│   │   │   └── impl/AuthServiceImpl.java
│   │   └── config/
│   │       └── SaTokenConfig.java   # Sa-Token 配置
│   │
│   ├── resume/                      # 简历模块
│   │   ├── controller/
│   │   │   └── ResumeController.java
│   │   ├── domain/
│   │   │   └── Resume.java
│   │   ├── dto/
│   │   │   ├── ResumeUploadRequest.java
│   │   │   ├── ResumeVO.java
│   │   │   └── ResumeParseResult.java
│   │   ├── repository/
│   │   │   └── ResumeRepository.java
│   │   ├── service/
│   │   │   ├── ResumeService.java
│   │   │   ├── impl/ResumeServiceImpl.java
│   │   │   └── ResumeParseService.java  # 百炼解析服务
│   │   └── enums/
│   │       └── ResumeStatus.java    # 解析状态枚举
│   │
│   ├── position/                    # 目标职位模块
│   │   ├── controller/
│   │   │   └── PositionController.java
│   │   ├── domain/
│   │   │   └── TargetPosition.java
│   │   ├── dto/
│   │   │   ├── PositionRequest.java
│   │   │   └── PositionVO.java
│   │   ├── repository/
│   │   │   └── PositionRepository.java
│   │   └── service/
│   │       ├── PositionService.java
│   │       └── impl/PositionServiceImpl.java
│   │
│   ├── report/                      # 评估报告模块
│   │   ├── controller/
│   │   │   └── ReportController.java
│   │   ├── domain/
│   │   │   └── Report.java
│   │   ├── dto/
│   │   │   ├── ReportRequest.java
│   │   │   ├── ReportVO.java
│   │   │   └── ReportContent.java   # 结构化报告内容
│   │   ├── repository/
│   │   │   └── ReportRepository.java
│   │   └── service/
│   │       ├── ReportService.java
│   │       └── impl/ReportServiceImpl.java
│   │
│   ├── interview/                   # 面试模块
│   │   ├── controller/
│   │   │   └── InterviewController.java
│   │   ├── domain/
│   │   │   ├── InterviewSession.java
│   │   │   └── InterviewQuestion.java  # 单题记录
│   │   ├── dto/
│   │   │   ├── StartInterviewRequest.java
│   │   │   ├── AnswerRequest.java
│   │   │   ├── InterviewEvent.java  # SSE 事件体
│   │   │   └── InterviewSummaryVO.java
│   │   ├── repository/
│   │   │   ├── InterviewSessionRepository.java
│   │   │   └── InterviewQuestionRepository.java
│   │   ├── service/
│   │   │   ├── InterviewService.java
│   │   │   ├── impl/InterviewServiceImpl.java
│   │   │   └── InterviewSessionManager.java  # 会话状态管理
│   │   └── enums/
│   │       ├── InterviewRound.java  # 一面/二面/三面
│   │       └── QuestionStatus.java  # 待答/已答/已跳过
│   │
│   ├── aicore/                      # AI 核心模块
│   │   ├── advisor/
│   │   │   ├── SafeValidAdvisor.java     # 安全过滤（输入+输出）
│   │   │   ├── ReReadingAdvisor.java     # 问题重读
│   │   │   └── MyLogAdvisor.java         # Token 日志
│   │   ├── rag/
│   │   │   ├── RagService.java           # RAG 检索服务
│   │   │   ├── MyTokenTextSplitter.java  # 文档切片
│   │   │   └── QueryAugmenterConfig.java # 查询增强配置
│   │   ├── memory/
│   │   │   ├── InterviewChatMemory.java  # 面试对话记忆
│   │   │   └── ChatMemoryStore.java      # 记忆存储（MySQL）
│   │   ├── prompt/
│   │   │   ├── ReportPrompt.java         # 评估报告 Prompt
│   │   │   └── InterviewPrompt.java      # 面试 Prompt
│   │   └── service/
│   │       ├── AiService.java            # LLM 调用封装
│   │       └── impl/AiServiceImpl.java
│   │
│   └── storage/                     # 文件存储模块
│       ├── controller/
│       │   └── FileController.java  # 文件上传/下载
│       ├── service/
│       │   ├── FileStorageService.java     # 接口
│       │   └── impl/MinioFileStorage.java  # MinIO 实现
│       └── config/
│           └── MinioConfig.java     # MinIO 配置
│
├── src/main/resources/
│   ├── application.yml              # 主配置
│   ├── application-dev.yml          # 开发环境
│   ├── application-prod.yml         # 生产环境
│   ├── db/                          # Liquibase
│   │   └── changelog/
│   │       ├── db.changelog-master.xml
│   │       ├── 001-create-users.sql
│   │       ├── 002-create-resumes.sql
│   │       ├── 003-create-positions.sql
│   │       ├── 004-create-reports.sql
│   │       └── 005-create-interviews.sql
│   └── document/                    # 知识库文档（可选）
│
├── Dockerfile                       # 后端 Docker 构建
└── docker-compose.yml               # 全部服务编排
```

### 3.2 前端项目结构

```
offer-agent-frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── Dockerfile                       # 前端 Docker 构建
├── nginx.conf                       # Nginx 配置
├── index.html
├── src/
│   ├── main.ts                      # 入口
│   ├── App.vue                      # 根组件
│   ├── api/                         # API 层
│   │   ├── request.ts               # Axios 实例 + 拦截器
│   │   ├── auth.ts                  # 认证 API
│   │   ├── resume.ts                # 简历 API
│   │   ├── position.ts              # 职位 API
│   │   ├── report.ts               # 报告 API
│   │   └── interview.ts            # 面试 API
│   ├── router/
│   │   └── index.ts                 # 路由配置
│   ├── stores/
│   │   ├── user.ts                  # 用户状态
│   │   └── interview.ts            # 面试状态
│   ├── views/
│   │   ├── login/                   # 登录/注册页
│   │   │   └── LoginView.vue
│   │   ├── home/                    # 首页
│   │   │   └── HomeView.vue
│   │   ├── resume/                  # 简历页
│   │   │   ├── UploadView.vue       # 上传简历
│   │   │   └── DetailView.vue       # 简历详情
│   │   ├── position/                # 职位页
│   │   │   └── PositionForm.vue     # 添加职位
│   │   ├── settings/                 # 设置页
│   │   │   └── SettingsView.vue    # 个人设置 + API Key
│   │   ├── report/                  # 报告页
│   │   │   └── ReportView.vue       # 评估报告展示
│   │   └── interview/               # 面试页
│   │       ├── PrepareView.vue      # 面试准备
│   │       ├── ChatView.vue         # 面试对话（SSE）
│   │       └── SummaryView.vue      # 面试总结
│   ├── components/
│   │   ├── AppLayout.vue            # 布局组件
│   │   ├── SseChat.vue              # SSE 流式对话组件
│   │   └── ScoreCard.vue            # 评分卡片组件
│   └── styles/
│       └── global.css
└── .env.development                 # 开发环境变量
└── .env.production                  # 生产环境变量
```

---

## 4. 数据库设计

### 4.1 ER 图（文字版）

```
User (1) ──→ Resume (N) ──→ TargetPosition (N)
  │                              │
  │                              │
  └──→ Report (N) ←─────────────┘
  │
  └──→ InterviewSession (N)
           │
           └──→ InterviewQuestion (N)  ← 每轮每题的记录
```

### 4.2 完整 DDL

#### 4.2.1 users（用户表）

```sql
CREATE TABLE users (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(100)    NOT NULL UNIQUE COMMENT '邮箱，唯一',
    password_hash   VARCHAR(255)    NOT NULL COMMENT 'BCrypt 加密密码',
    nickname        VARCHAR(50)     DEFAULT NULL COMMENT '昵称',
    status          TINYINT         DEFAULT 1 COMMENT '0-禁用 1-正常',
    last_login_at   DATETIME        DEFAULT NULL COMMENT '最后登录时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

#### 4.2.2 resumes（简历表）

```sql
CREATE TABLE resumes (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL COMMENT '所属用户',
    name            VARCHAR(100)    NOT NULL COMMENT '简历名称，如 2026-08-31 版本',
    file_url        VARCHAR(500)    NOT NULL COMMENT 'MinIO 文件路径',
    file_size       INT             DEFAULT 0 COMMENT '文件大小(字节)',
    page_count      INT             DEFAULT 0 COMMENT 'PDF 页数',
    parsed_text     LONGTEXT        DEFAULT NULL COMMENT '百炼解析后的纯文本',
    tech_stack      JSON            DEFAULT NULL COMMENT '提取的技术栈列表',
    work_years      DECIMAL(3,1)    DEFAULT NULL COMMENT '工作年限',
    education       VARCHAR(50)     DEFAULT NULL COMMENT '最高学历',
    summary         TEXT            DEFAULT NULL COMMENT 'AI 提取的简历摘要',
    is_default      TINYINT         DEFAULT 0 COMMENT '0-否 1-默认简历',
    status          TINYINT         DEFAULT 0 COMMENT '0-解析中 1-解析完成 2-解析失败',
    fail_reason     VARCHAR(500)    DEFAULT NULL COMMENT '解析失败原因',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历表';
```

#### 4.2.3 target_positions（目标职位表）

```sql
CREATE TABLE target_positions (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL COMMENT '所属用户',
    resume_id       BIGINT          NOT NULL COMMENT '关联简历',
    title           VARCHAR(200)    NOT NULL COMMENT '职位名称，如 Java高级开发工程师',
    company         VARCHAR(200)    DEFAULT NULL COMMENT '公司名称',
    jd_text         TEXT            NOT NULL COMMENT '职位描述全文',
    location        VARCHAR(100)    DEFAULT NULL COMMENT '工作地点',
    salary_range    VARCHAR(50)     DEFAULT NULL COMMENT '薪资范围',
    is_default      TINYINT         DEFAULT 0 COMMENT '0-否 1-默认职位',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_resume_id (resume_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (resume_id) REFERENCES resumes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标职位表';
```

#### 4.2.4 reports（评估报告表）

```sql
CREATE TABLE reports (
    id                      BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id                 BIGINT          NOT NULL,
    resume_id               BIGINT          NOT NULL,
    position_id             BIGINT          NOT NULL,
    match_score             DECIMAL(5,2)    DEFAULT NULL COMMENT '匹配度百分比，如 72.00',
    tech_stack_analysis     JSON            DEFAULT NULL COMMENT '技术栈分析 JSON',
    highlights              JSON            DEFAULT NULL COMMENT '亮点提炼 JSON 数组',
    weaknesses              JSON            DEFAULT NULL COMMENT '短板提醒 JSON 数组',
    full_report             LONGTEXT        DEFAULT NULL COMMENT '报告原始文本',
    status                  TINYINT         DEFAULT 0 COMMENT '0-生成中 1-完成 2-失败',
    error_message           VARCHAR(500)    DEFAULT NULL COMMENT '失败原因',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_resume_id (resume_id),
    INDEX idx_position_id (position_id),
    INDEX idx_status (status),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (resume_id) REFERENCES resumes(id),
    FOREIGN KEY (position_id) REFERENCES target_positions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估报告表';
```

#### 4.2.5 interview_sessions（面试会话表）

```sql
CREATE TABLE interview_sessions (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    resume_id       BIGINT          NOT NULL,
    position_id     BIGINT          NOT NULL,
    current_round   TINYINT         DEFAULT 1 COMMENT '当前轮次：1-一面 2-二面 3-三面',
    current_question INT            DEFAULT 0 COMMENT '当前题目序号 0-10',
    total_questions  INT            DEFAULT 0 COMMENT '总回答题数',
    score           JSON            DEFAULT NULL COMMENT '各维度评分 JSON',
    summary         TEXT            DEFAULT NULL COMMENT '面试总结文本',
    duration_seconds INT            DEFAULT 0 COMMENT '面试时长(秒)',
    status          TINYINT         DEFAULT 0 COMMENT '0-进行中 1-已完成 2-已过期 3-已中断',
    expired_at      DATETIME        DEFAULT NULL COMMENT '过期时间(中断后+1小时)',
    started_at      DATETIME        DEFAULT NULL COMMENT '面试开始时间',
    finished_at     DATETIME        DEFAULT NULL COMMENT '面试结束时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_expired_at (expired_at),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (resume_id) REFERENCES resumes(id),
    FOREIGN KEY (position_id) REFERENCES target_positions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试会话表';
```

#### 4.2.6 interview_questions（面试题目表）

```sql
CREATE TABLE interview_questions (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT          NOT NULL COMMENT '所属面试会话',
    round           TINYINT         NOT NULL COMMENT '轮次：1-一面 2-二面 3-三面',
    question_index  INT             NOT NULL COMMENT '题号 1-10',
    question_text   TEXT            NOT NULL COMMENT 'AI 提出的问题',
    user_answer     LONGTEXT        DEFAULT NULL COMMENT '用户回答',
    feedback        LONGTEXT        DEFAULT NULL COMMENT 'AI 反馈',
    score           DECIMAL(3,1)    DEFAULT NULL COMMENT '本题评分 1-10',
    status          TINYINT         DEFAULT 0 COMMENT '0-待答 1-已答 2-已跳过',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_round (round),
    FOREIGN KEY (session_id) REFERENCES interview_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试题目表';
```

### 4.3 pgvector 向量表

由 Spring AI 自动创建，默认表名 `vector_store`，无需手动建表。但需要确保数据库已安装 pgvector 扩展。

```sql
-- 在 PostgreSQL 中执行（由 Docker 镜像自动初始化）
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
```

### 4.4 Liquibase 配置

```xml
<!-- db/changelog/db.changelog-master.xml -->
<databaseChangeLog>
    <include file="db/changelog/001-create-users.sql"/>
    <include file="db/changelog/002-create-resumes.sql"/>
    <include file="db/changelog/003-create-positions.sql"/>
    <include file="db/changelog/004-create-reports.sql"/>
    <include file="db/changelog/005-create-interviews.sql"/>
</databaseChangeLog>
```

每个 SQL 文件用 `--changeset author:id` 标记，支持回滚。

---

## 5. API 设计

### 5.1 统一规范

| 规范 | 说明 |
|------|------|
| 基础路径 | `/api/v1` |
| 响应格式 | 统一 `ApiResult<T>` |
| 分页格式 | 统一 `PageResult<T>` |
| 认证方式 | Header: `Authorization: Bearer <token>` |
| 内容类型 | 请求: `application/json`，文件上传: `multipart/form-data` |
| SSE 路径 | 单独端点，`text/event-stream` |

### 5.2 统一响应体

```java
// 成功
{
    "code": 200,
    "message": "success",
    "data": { ... },
    "traceId": "a1b2c3d4"
}

// 失败
{
    "code": 400,
    "message": "邮箱已被注册",
    "data": null,
    "traceId": "a1b2c3d4"
}

// 分页
{
    "code": 200,
    "message": "success",
    "data": {
        "records": [ ... ],
        "total": 100,
        "page": 1,
        "size": 10
    },
    "traceId": "a1b2c3d4"
}
```

### 5.3 完整 API 列表

#### 5.3.1 认证模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/auth/register` | 注册 | 否 |
| POST | `/api/v1/auth/login` | 登录 | 否 |
| POST | `/api/v1/auth/refresh` | 刷新 Token | 否 |
| POST | `/api/v1/auth/logout` | 退出 | 是 |
| GET | `/api/v1/auth/me` | 获取当前用户信息 | 是 |
| PUT | `/api/v1/auth/profile` | 修改个人信息 | 是 |
| PUT | `/api/v1/auth/api-key` | 配置/更新 DeepSeek API Key | 是 |
| DELETE | `/api/v1/auth/api-key` | 删除 API Key（恢复免费层） | 是 |

**注册请求：**
```json
{
    "email": "user@example.com",
    "password": "abc12345",
    "nickname": "小明"  // 可选
}
```

**注册响应：**
```json
{
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 7200,
    "user": {
        "id": 1,
        "email": "user@example.com",
        "nickname": "小明"
    }
}
```

**登录请求：**
```json
{
    "email": "user@example.com",
    "password": "abc12345"
}
```

**登录响应：** 同注册响应

#### 5.3.2 简历模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/resumes` | 上传简历 | 是 |
| GET | `/api/v1/resumes` | 简历列表 | 是 |
| GET | `/api/v1/resumes/{id}` | 简历详情 | 是 |
| DELETE | `/api/v1/resumes/{id}` | 删除简历 | 是 |
| PUT | `/api/v1/resumes/{id}/default` | 设为默认简历 | 是 |

**上传简历请求：** `multipart/form-data`
```
file: (PDF 文件)
name: "2026-08-31 版本"
```

**上传简历响应：**
```json
{
    "id": 1,
    "name": "2026-08-31 版本",
    "fileUrl": "/api/v1/files/xxx.pdf",
    "fileSize": 102400,
    "pageCount": 2,
    "status": 0,       // 0-解析中
    "createdAt": "2026-08-31T10:00:00"
}
```

**简历列表响应：**
```json
{
    "records": [
        {
            "id": 1,
            "name": "2026-08-31 版本",
            "status": 1,       // 1-解析完成
            "workYears": 7.0,
            "education": "本科",
            "techStack": ["Java", "Spring Boot", "MySQL"],
            "isDefault": 1,
            "createdAt": "2026-08-31T10:00:00"
        }
    ],
    "total": 5,
    "page": 1,
    "size": 10
}
```

**简历详情响应：**
```json
{
    "id": 1,
    "name": "2026-08-31 版本",
    "fileUrl": "/api/v1/files/xxx.pdf",
    "status": 1,
    "workYears": 7.0,
    "education": "本科",
    "techStack": ["Java", "Spring Boot", "MySQL", "Redis"],
    "summary": "7年Java开发经验...",
    "projects": [
        {"name": "智能工枢系统", "role": "技术负责人", "techStack": [...]}
    ],
    "isDefault": 1,
    "createdAt": "2026-08-31T10:00:00"
}
```

#### 5.3.3 目标职位模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/positions` | 添加目标职位 | 是 |
| GET | `/api/v1/positions` | 职位列表（按简历筛选） | 是 |
| GET | `/api/v1/positions/{id}` | 职位详情 | 是 |
| DELETE | `/api/v1/positions/{id}` | 删除职位 | 是 |
| PUT | `/api/v1/positions/{id}/default` | 设为默认职位 | 是 |

**添加职位请求：**
```json
{
    "resumeId": 1,
    "title": "Java高级开发工程师",
    "company": "字节跳动",
    "jdText": "职位描述：负责核心业务系统设计开发...",
    "location": "上海",
    "salaryRange": "30-50K"
}
```

#### 5.3.4 评估报告模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/reports` | 生成评估报告 | 是 |
| GET | `/api/v1/reports` | 报告列表 | 是 |
| GET | `/api/v1/reports/{id}` | 报告详情 | 是 |
| DELETE | `/api/v1/reports/{id}` | 删除报告 | 是 |

**生成报告请求：**
```json
{
    "resumeId": 1,
    "positionId": 1
}
```

**生成报告响应（SSE 流式或轮询，建议先返回 reportId 异步生成）：**
```json
{
    "id": 1,
    "status": 0,   // 0-生成中
    "createdAt": "2026-08-31T10:00:00"
}
```

**前端轮询：** 前端每隔 3 秒 GET `/api/v1/reports/{id}`，直到 `status=1`。

**报告详情响应：**
```json
{
    "id": 1,
    "resumeId": 1,
    "positionId": 1,
    "matchScore": 72.00,
    "techStackAnalysis": {
        "matched": ["Java", "Spring Boot", "MySQL"],
        "partial": ["微服务"],
        "missing": ["高并发", "分布式事务"],
        "suggestion": "补充高并发项目经验"
    },
    "highlights": [
        "7年Java研发经验，项目经验丰富",
        "主导过AI招聘系统从0到1交付",
        "具备微服务架构设计与团队管理能力"
    ],
    "weaknesses": [
        "缺少系统设计方面的项目描述",
        "高并发场景未涉及",
        "技术栈描述偏旧，缺少AI相关能力展示"
    ],
    "fullReport": "完整报告文本...",
    "status": 1,
    "createdAt": "2026-08-31T10:00:00"
}
```

#### 5.3.5 面试模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/interviews` | 开始面试 | 是 |
| GET | `/api/v1/interviews` | 面试列表 | 是 |
| GET | `/api/v1/interviews/{id}` | 面试详情 | 是 |
| POST | `/api/v1/interviews/{id}/start-round` | 开始下一轮 | 是 |
| POST | `/api/v1/interviews/{id}/answer` | 回答当前题目（SSE 流式返回） | 是 |
| POST | `/api/v1/interviews/{id}/skip` | 跳过当前题 | 是 |
| POST | `/api/v1/interviews/{id}/end` | 结束面试 | 是 |
| GET | `/api/v1/interviews/{id}/summary` | 面试总结 | 是 |

**开始面试请求：**
```json
{
    "resumeId": 1,
    "positionId": 1
}
```

**开始面试响应：**
```json
{
    "id": 1,
    "currentRound": 1,
    "currentQuestion": 0,
    "status": 0,   // 进行中
    "rounds": [
        {
            "round": 1,
            "name": "技术一面",
            "description": "基础技术能力考察",
            "totalQuestions": 10,
            "questions": []
        }
    ]
}
```

**回答题目（SSE 端点）：**

`POST /api/v1/interviews/{id}/answer`

请求体：
```json
{
    "questionId": 1,
    "answer": "我做的智能工枢系统是一个面向人力资源行业的招聘管理系统..."
}
```

**SSE 响应格式（`text/event-stream`）：**

```
event: feedback
data: {"type":"feedback","content":"回答不错，项目描述清晰，但建议补充你在其中的具体技术难点和解决方案。"}

event: next_question
data: {"type":"question","questionId":2,"questionIndex":2,"content":"你说到用了微服务架构，能具体讲讲你怎么拆分服务的吗？"}

event: round_end
data: {"type":"round_end","round":1,"summary":"技术一面完成，答了10题，正确率80%","nextRound":2}

event: complete
data: {"type":"complete","sessionId":1}
```

**面试总结响应：**
```json
{
    "id": 1,
    "resumeTitle": "李道奇-2026",
    "positionTitle": "Java高级开发工程师",
    "rounds": [
        {
            "round": 1,
            "name": "技术一面",
            "score": 8.0,
            "totalQuestions": 10,
            "answeredQuestions": 10,
            "summary": "基础技术能力扎实，Spring框架掌握良好"
        },
        {
            "round": 2,
            "name": "技术二面",
            "score": 7.5,
            "totalQuestions": 10,
            "answeredQuestions": 8,
            "summary": "深入理解微服务架构，但高并发优化需加强"
        }
    ],
    "overallScore": 7.8,
    "dimensions": {
        "techDepth": 8.0,
        "projectUnderstanding": 7.5,
        "expressionClarity": 8.0
    },
    "strengths": ["项目经验丰富", "架构理解深入"],
    "weaknesses": ["高并发经验不足", "系统设计需加强"],
    "suggestions": ["重点复习分布式事务", "准备系统设计案例"],
    "durationSeconds": 1800,
    "status": 1,
    "finishedAt": "2026-08-31T11:30:00"
}
```

#### 5.3.6 文件模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/files/{filePath}` | 获取文件（MinIO 代理） | 是 |

#### 5.3.7 健康检查

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/health` | 健康检查 | 否 |

---

## 6. AI 核心架构

### 6.1 整体链路

```
用户请求
    ↓
SafeValidAdvisor（输入安全检测）
    ↓
ReReadingAdvisor（重复问题，加深理解）
    ↓
MessageChatMemoryAdvisor（注入对话记忆，最近10条）
    ↓
RetrievalAugmentationAdvisor（RAG：简历+JD 向量检索）
    ↓
LLM（DeepSeek）
    ↓
SafeValidAdvisor（输出安全检测）
    ↓
SSE 流式输出到前端
```

### 6.2 Advisor Chain 配置

```java
// AiCoreConfig.java
@Bean
public ChatClient.Builder chatClientBuilder(
        ChatModel chatModel,
        SafeValidAdvisor safeValidAdvisor,
        ReReadingAdvisor reReadingAdvisor,
        MessageChatMemoryAdvisor chatMemoryAdvisor) {
    
    return ChatClient.builder(chatModel)
            .defaultAdvisors(
                    safeValidAdvisor,                   // 最高优先级
                    reReadingAdvisor,                   // 第二
                    chatMemoryAdvisor,                  // 第三
                    new MyLogAdvisor()                  // 日志
            );
}

// 注意：RetrievalAugmentationAdvisor 不在默认链中，
// 在面试场景中按需添加到 ChatClient
```

### 6.3 RAG 检索设计

#### 6.3.1 知识库初始化

```java
// ResumeParseService.java
// 简历解析完成后，异步触发的 RAG 入库流程
void indexResumeToVectorStore(Resume resume) {
    // 1. 获取解析后的纯文本
    String text = resume.getParsedText();
    
    // 2. 切片
    List<Document> chunks = tokenTextSplitter.split(text);
    
    // 3. 添加元数据（用于检索过滤）
    chunks.forEach(doc -> {
        doc.getMetadata().put("user_id", resume.getUserId());
        doc.getMetadata().put("resume_id", resume.getId());
        doc.getMetadata().put("type", "resume");
    });
    
    // 4. 向量化存储
    vectorStore.add(chunks);
}
```

#### 6.3.2 检索时过滤

```java
// RagService.java
List<Document> searchResumeKnowledge(Long userId, String query) {
    // 构建检索请求，按 user_id 过滤
    var searchRequest = SearchRequest.builder()
            .query(query)
            .topK(5)
            .similarityThreshold(0.6)
            .filterExpression("user_id == " + userId)  // 数据隔离
            .build();
    
    return vectorStore.similaritySearch(searchRequest);
}
```

#### 6.3.3 查询增强配置

```java
// QueryAugmenterConfig.java
@Bean
QueryAugmenter queryAugmenter() {
    PromptTemplate emptyContextPrompt = new PromptTemplate("""
        用户的问题是: {query}
        
        如果检索到的文档中没有相关信息，你必须直接回答"找不到相关文档，无法回答问题，你可以联系管理员。"，不要自己编造答案。
        """);
    
    return ContextualQueryAugmenter.builder()
            .allowEmptyContext(true)
            .emptyContextPromptTemplate(emptyContextPrompt)
            .build();
}
```

### 6.4 核心 Prompt 设计

#### 6.4.1 评估报告 Prompt

```
角色：你是一位资深技术面试官和简历顾问，擅长分析技术简历与目标职位的匹配度。

任务：分析以下简历内容与目标职位要求的匹配情况，生成结构化的评估报告。

简历内容：
{resume_text}

目标职位：
职位名称：{position_title}
公司：{company}
职位描述：{jd_text}

要求：
1. 给出匹配度百分比（0-100），精确到整数
2. 分析技术栈匹配情况（完全匹配、部分匹配、缺失）
3. 提炼简历中3-5个亮点
4. 指出3-5个短板或可能被面试官挑战的点
5. 注意：不要编造简历中没有的内容，只基于用户提供的简历数据做分析。

输出格式：必须是 JSON 格式，严格按照以下结构：
{
    "match_score": 整数,
    "tech_stack_analysis": {
        "matched": ["技术栈1", "技术栈2"],
        "partial": ["技术栈3"],
        "missing": ["技术栈4"],
        "suggestion": "建议描述"
    },
    "highlights": ["亮点1", "亮点2"],
    "weaknesses": ["短板1", "短板2"]
}

注意：不要编造简历中没有的内容，只基于用户提供的简历数据做分析。
```

#### 6.4.2 面试 Prompt

```
角色：你是一位资深技术面试官，正在面试一位技术研发岗位的候选人。
你的面试风格：专业、严谨但不咄咄逼人，喜欢深入追问技术细节。

候选人信息：
- 工作年限：{work_years}年
- 技术栈：{tech_stack}
- 项目经历：{projects}

目标职位：{position_title} - {company}
职位要求：{jd_text}

当前面试状态：
- 当前轮次：第{current_round}轮（一面-基础 / 二面-深入 / 三面-综合）
- 当前题目：第{current_question_index}题（共10题）
- 已答题目：{history}

一轮面试的规则：
- 每轮10题，从基础到深入逐步递进
- 一面基础：语言基础、框架用法、项目基础问题
- 二面深入：底层原理、性能优化、复杂场景设计
- 三面综合：架构设计、技术选型、开放性难题

当前你的任务：
1. 如果这是本轮第一题，给出一个开场白（如"你好，我们开始第一轮技术面试"）
2. 如果用户回答了上一题，先给出简短反馈（1-2句话），然后提出下一题
3. 如果是追问，基于用户的回答深入挖掘

注意：
- 不要一次性问多个问题，一次只问一个
- 给用户回答留出空间，不要直接告诉答案
- 当用户回答后，给出建设性反馈，而不是简单说"好"或"不好"
- 如果用户回答中提到了技术细节，可以追问深入
- 10题满后，生成本轮总结，询问是否继续下一轮
```

#### 6.4.3 面试反馈 Prompt

```
你是一位资深技术面试官，请对候选人的回答进行评判。

问题：{question}
候选人的回答：{answer}
候选人的背景：{work_years}年经验，技术栈：{tech_stack}

请给出：
1. 简短反馈（1-2句话，指出优点和不足）
2. 评分（1-10分）
3. 如果是追问，判断是否需要继续深入追问

输出格式（JSON）：
{
    "feedback": "反馈内容",
    "score": 整数(1-10),
    "should_follow_up": true/false,
    "follow_up_question": "如果应该追问，写追问内容，否则留空"
}
```

### 6.5 面试会话状态机

```
[开始面试] → 技术一面(Q1-Q10) → [继续] → 技术二面(Q1-Q10) → [继续] → 技术三面(Q1-Q10) → [结束] → 面试总结
                                ↘ [中断/1小时内] → 恢复面试
                                ↘ [中断/超1小时] → 已过期
                                ↘ [主动结束] → 面试总结(已有数据)
```

**状态流转代码设计：**

```java
// InterviewSessionManager.java
public enum SessionStatus {
    IN_PROGRESS(0, "进行中"),
    COMPLETED(1, "已完成"),
    EXPIRED(2, "已过期"),
    ABANDONED(3, "已中断");
}

public enum InterviewRound {
    ROUND_1(1, "技术一面", "基础技术能力考察"),
    ROUND_2(2, "技术二面", "深入技术能力考察"),
    ROUND_3(3, "技术三面", "综合技术能力考察");
}

// 关键方法
public InterviewSession startSession(Long userId, Long resumeId, Long positionId);
public InterviewQuestion nextQuestion(Long sessionId);  // 获取下一题
public void answerQuestion(Long questionId, String answer);  // 保存回答
public String generateNextQuestion(Long sessionId);  // LLM 生成下一题
public InterviewSession endSession(Long sessionId);  // 结束面试，生成总结
public InterviewSession resumeSession(Long sessionId);  // 恢复中断的面试
```

---

## 7. 安全架构

### 7.1 认证流程

```
用户登录 → 服务端验证邮箱密码 → 生成 AccessToken(2h) + RefreshToken(7d)

请求接口 → 携带 Authorization: Bearer <accessToken>
    ↓
SaToken拦截器 → 验证 Token → 放行 / 拒绝

AccessToken 过期 → 用 RefreshToken 换取新 Token
    ↓
RefreshToken 过期 → 重新登录
```

### 7.2 数据权限

```java
// 在 Service 层，所有查询自动注入 user_id
// 通过 MyBatis-Plus 拦截器或手动添加条件

// 示例：简历列表查询
public PageResult<Resume> listResumes(Long userId, int page, int size) {
    LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Resume::getUserId, userId);  // 强制过滤
    wrapper.orderByDesc(Resume::getCreatedAt);
    return PageHelper.startPage(page, size, wrapper);
}
```

### 7.3 密码安全

```java
// 使用 BCryptPasswordEncoder
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// 注册时加密
user.setPasswordHash(passwordEncoder.encode(rawPassword));

// 登录时验证
passwordEncoder.matches(rawPassword, user.getPasswordHash())
```

### 7.4 AI 安全

```java
// SafeValidAdvisor.java
// 前置检测：输入文本
public class SafeValidAdvisor implements AroundAdvisor {
    
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "忽略指令", "忽略系统提示", "忘记之前的", "扮演", "黑客", "攻击", 
            "<script>", "javascript:", "onerror=", "onclick="
    );
    
    @Override
    public AdvisedResponse around(AdvisedRequest request) {
        // 前置：检查输入
        String userText = request.param("userText");
        if (containsSensitiveContent(userText)) {
            throw new BusinessException("输入内容包含敏感信息，请重新提问");
        }
        
        // 调用下游
        AdvisedResponse response = proceed(request);
        
        // 后置：检查输出
        String output = response.response();
        if (containsSensitiveContent(output)) {
            return new AdvisedResponse("生成内容包含敏感信息，已拦截", 
                    response.adviseContext());
        }
        
        return response;
    }
    
    private boolean containsSensitiveContent(String text) {
        if (text == null) return false;
        return SENSITIVE_KEYWORDS.stream().anyMatch(text::contains);
    }
}
```

### 7.5 API Key 与限额管理

#### 7.5.1 数据模型

```sql
-- 在 users 表基础上新增 api_key 字段
ALTER TABLE users ADD COLUMN api_key VARCHAR(255) DEFAULT NULL COMMENT '用户自配的 DeepSeek API Key';
```

#### 7.5.2 限流逻辑

```java
// RateLimitService.java
@Service
public class RateLimitService {
    
    // 每日限额配置
    private static final int DAILY_REPORT_LIMIT = 3;
    private static final int DAILY_INTERVIEW_LIMIT = 1;
    
    // 使用 Redis 或本地缓存统计
    private final Map<String, Map<String, Integer>> dailyCounts = new ConcurrentHashMap<>();
    
    public boolean tryConsume(Long userId, String actionType) {
        String date = LocalDate.now().toString();
        String key = userId + ":" + actionType + ":" + date;
        
        int limit = switch (actionType) {
            case "report" -> DAILY_REPORT_LIMIT;
            case "interview" -> DAILY_INTERVIEW_LIMIT;
            default -> Integer.MAX_VALUE;
        };
        
        // 原子递增，判断是否超限
        return incrementAndCheck(key, limit);
    }
    
    public boolean hasApiKey(Long userId) {
        // 查询用户是否配置了自己的 API Key
        return userRepository.hasApiKey(userId);
    }
}
```

#### 7.5.3 AI 调用决策逻辑

```java
// AiService.java
public String callLLM(String prompt, Long userId) {
    // 1. 检查用户是否配置了自己的 API Key
    String userApiKey = userRepository.getApiKey(userId);
    
    if (StringUtils.hasText(userApiKey)) {
        // 用自己的 Key → 不限量，零成本
        return callWithKey(prompt, userApiKey);
    }
    
    // 2. 用平台 Key → 检查限额
    if (!rateLimitService.tryConsume(userId, getActionType())) {
        throw new BusinessException(429, "今日次数已用完，请明日再试或配置自己的 API Key");
    }
    
    // 3. 调用平台 Key
    return callWithKey(prompt, platformApiKey);
}
```

#### 7.5.4 前端展示逻辑

```typescript
// 在个人设置页显示 API Key 配置
// 在后端返回的限额信息中附带剩余次数

// 获取用户信息 API 扩展
GET /api/v1/auth/me
Response: {
    ...,
    "apiKeyConfigured": false,
    "dailyQuota": {
        "report": { "used": 2, "limit": 3 },
        "interview": { "used": 1, "limit": 1 }
    }
}
```

---

## 8. 前端架构

### 8.1 路由设计

```typescript
// router/index.ts
const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/login/LoginView.vue'),
        meta: { requiresAuth: false }
    },
    {
        path: '/',
        component: () => import('@/components/AppLayout.vue'),
        meta: { requiresAuth: true },
        children: [
            {
                path: '',
                name: 'Home',
                component: () => import('@/views/home/HomeView.vue')
            },
            {
                path: 'resume/upload',
                name: 'ResumeUpload',
                component: () => import('@/views/resume/UploadView.vue')
            },
            {
                path: 'resume/:id',
                name: 'ResumeDetail',
                component: () => import('@/views/resume/DetailView.vue')
            },
            {
                path: 'resume/:id/position/add',
                name: 'AddPosition',
                component: () => import('@/views/position/PositionForm.vue')
            },
            {
                path: 'report/:id',
                name: 'ReportDetail',
                component: () => import('@/views/report/ReportView.vue')
            },
            {
                path: 'interview/prepare/:resumeId/:positionId',
                name: 'InterviewPrepare',
                component: () => import('@/views/interview/PrepareView.vue')
            },
            {
                path: 'interview/:id',
                name: 'InterviewChat',
                component: () => import('@/views/interview/ChatView.vue')
            },
            {
                path: 'interview/:id/summary',
                name: 'InterviewSummary',
                component: () => import('@/views/interview/SummaryView.vue')
            },
            {
                path: 'settings',
                name: 'Settings',
                component: () => import('@/views/settings/SettingsView.vue')
            }
        ]
    }
];
```

### 8.2 关键组件：SSE 对话组件

```vue
<!-- SseChat.vue - 面试对话核心组件 -->
<template>
    <div class="sse-chat">
        <div v-for="msg in messages" :key="msg.id" class="message-bubble">
            <div v-if="msg.role === 'assistant'" class="assistant-message">
                <span class="role-tag">面试官</span>
                <div class="content" v-html="renderMarkdown(msg.content)"></div>
            </div>
            <div v-else class="user-message">
                <span class="role-tag">我</span>
                <div class="content">{{ msg.content }}</div>
            </div>
        </div>
        
        <!-- 流式输出区域 -->
        <div v-if="streamingContent" class="message-bubble assistant-message streaming">
            <span class="role-tag">面试官</span>
            <div class="content" v-html="renderMarkdown(streamingContent)"></div>
            <span class="cursor-blink">|</span>
        </div>
        
        <!-- 输入区域 -->
        <div class="input-area" v-if="isWaitingAnswer">
            <el-input
                v-model="userAnswer"
                type="textarea"
                :rows="3"
                placeholder="请输入你的回答..."
                @keydown.ctrl.enter="submitAnswer"
            />
            <el-button type="primary" @click="submitAnswer" :loading="submitting">
                发送回答
            </el-button>
        </div>
    </div>
</template>

<script setup lang="ts">
// SSE 连接管理
const connectSSE = (url: string) => {
    const eventSource = new EventSource(url);
    
    eventSource.addEventListener('feedback', (event) => {
        const data = JSON.parse(event.data);
        streamingContent.value += data.content;
    });
    
    eventSource.addEventListener('next_question', (event) => {
        const data = JSON.parse(event.data);
        messages.value.push({ role: 'assistant', content: streamingContent.value });
        streamingContent.value = data.content;
        currentQuestionId.value = data.questionId;
        isWaitingAnswer.value = true;
    });
    
    eventSource.addEventListener('complete', (event) => {
        eventSource.close();
        // 跳转到总结页
        router.push(`/interview/${sessionId}/summary`);
    });
    
    eventSource.onerror = () => {
        // 断线重连逻辑
        console.error('SSE 连接中断，尝试重连...');
        setTimeout(() => connectSSE(url), 3000);
    };
};
</script>
```

### 8.3 API 请求层

```typescript
// api/request.ts
import axios from 'axios';
import { ElMessage } from 'element-plus';

const request = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
    timeout: 30000,
});

// 请求拦截器：自动注入 Token
request.interceptors.request.use((config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// 响应拦截器：统一错误处理
request.interceptors.response.use(
    (response) => {
        const res = response.data;
        if (res.code !== 200) {
            ElMessage.error(res.message);
            return Promise.reject(new Error(res.message));
        }
        return res;
    },
    (error) => {
        if (error.response?.status === 401) {
            // Token 过期，尝试刷新
            // 刷新失败，跳转登录页
        }
        ElMessage.error(error.message);
        return Promise.reject(error);
    }
);
```

---

## 9. 配置管理

### 9.1 application.yml 主配置

```yaml
server:
  port: 8080

spring:
  application:
    name: offer-agent
  
  datasource:
    url: jdbc:mysql://localhost:3306/offer_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_ROOT_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
  
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
  
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE
        dimension: 512
  
    embedding:
      transformer:
        tokenizer:
          uri: https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main/tokenizer.json
        model:
          uri: https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main/onnx/model_quantized.onnx

  # pgvector 数据源由 Spring AI 自动配置管理
  # 无需手动配置 datasource，vectorstore 配置已在上方指定

minio:
  endpoint: http://localhost:9000
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: offer-agent-files

bailian:
  api-key: ${BAILIAN_API_KEY}
  document-parser:
    url: https://api.bailian.aliyun.com/v1/document/parse

sa-token:
  token-name: Authorization
  timeout: 7200          # AccessToken 2小时
  active-timeout: -1
  is-concurrent: true
  is-share: false
  token-style: uuid
```

### 9.2 多环境配置

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/offer_agent
    username: root
    password: root

minio:
  endpoint: http://localhost:9000

# application-prod.yml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/offer_agent  # Docker 服务名
    username: root
    password: ${MYSQL_ROOT_PASSWORD}

minio:
  endpoint: http://minio:9000  # Docker 服务名
```

---

## 10. 错误处理体系

### 10.1 错误码

| 错误码 | 说明 | 场景 |
|--------|------|------|
| 200 | 成功 | |
| 400 | 参数错误 | 参数校验失败 |
| 401 | 未认证 | Token 过期或无效 |
| 403 | 无权限 | 访问他人资源 |
| 404 | 资源不存在 | 简历/报告/面试不存在 |
| 409 | 资源冲突 | 邮箱已注册 |
| 429 | 请求太频繁 | 限流 |
| 500 | 系统错误 | 服务器内部异常 |
| 1001 | AI 服务异常 | DeepSeek API 调用失败 |
| 1002 | 文档解析失败 | 百炼解析异常 |
| 1003 | 简历解析超时 | 解析超过 30 秒 |
| 2001 | 面试已过期 | 中断超过 1 小时 |
| 2002 | 面试已结束 | 重复操作已结束的面试 |

### 10.2 异常处理代码

```java
// BusinessException.java
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {
    private final int code;
    private final String message;
    
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    public static BusinessException resumeNotFound() {
        return new BusinessException(404, "简历不存在");
    }
    
    public static BusinessException interviewExpired() {
        return new BusinessException(2001, "面试已过期，请重新开始");
    }
    
    public static BusinessException emailAlreadyRegistered() {
        return new BusinessException(409, "该邮箱已被注册");
    }
}

// GlobalExceptionHandler.java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ApiResult.error(e.getCode(), e.getMessage());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResult.error(400, msg);
    }
    
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleUnknown(Exception e) {
        log.error("系统异常", e);
        return ApiResult.error(500, "服务器内部错误，请稍后重试");
    }
}
```

---

## 11. 部署架构

### 11.1 Docker Compose 编排

```yaml
# docker-compose.yml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: offer-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: offer_agent
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - offer-network
    restart: always

  postgres:
    image: pgvector/pgvector:pg16
    container_name: offer-pgvector
    environment:
      POSTGRES_DB: offer_agent_vector
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - pgvector-data:/var/lib/postgresql/data
    networks:
      - offer-network
    restart: always

  redis:
    image: redis:7-alpine
    container_name: offer-redis
    ports:
      - "6379:6379"
    networks:
      - offer-network
    restart: always

  minio:
    image: minio/minio:latest
    container_name: offer-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    ports:
      - "9000:9000"   # API
      - "9001:9001"   # 控制台
    volumes:
      - minio-data:/data
    networks:
      - offer-network
    restart: always

  backend:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: offer-backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}
      BAILIAN_API_KEY: ${BAILIAN_API_KEY}
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
    ports:
      - "8080:8080"
    depends_on:
      - mysql
      - postgres
      - minio
    networks:
      - offer-network
    restart: always

  frontend:
    build:
      context: ./offer-agent-frontend
      dockerfile: Dockerfile
    container_name: offer-frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - offer-network
    restart: always

volumes:
  mysql-data:
  pgvector-data:
  minio-data:

networks:
  offer-network:
    driver: bridge
```

### 11.2 后端 Dockerfile

```dockerfile
# Dockerfile - 多阶段构建
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/offer-agent-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 11.3 前端 Dockerfile

```dockerfile
# offer-agent-frontend/Dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 11.4 Nginx 配置

```nginx
# offer-agent-frontend/nginx.conf
server {
    listen 80;
    server_name _;
    
    # 前端静态文件
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;  # SPA 路由支持
    }
    
    # API 反向代理
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # SSE 支持（不缓冲）
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }
    
    # 上传文件大小限制
    client_max_body_size 10M;
}
```

### 11.5 CI/CD 配置

```yaml
# .github/workflows/deploy.yml
name: Deploy to Server

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Build Backend
        run: mvn package -DskipTests
      
      - name: Build Frontend
        working-directory: ./offer-agent-frontend
        run: |
          npm ci
          npm run build
      
      - name: Deploy to Server
        uses: appleboy/scp-action@v0.1.4
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          source: "./"
          target: "/opt/offer-agent/"
      
      - name: Restart Services
        uses: appleboy/ssh-action@v0.1.5
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          script: |
            cd /opt/offer-agent
            docker compose pull
            docker compose up -d --build
            docker image prune -f
```

---

## 12. 日志与可观测性

### 12.1 日志规范

```java
// 在 logback-spring.xml 中配置 traceId 注入
// 使用 MDC 贯穿整个请求

// 使用方式
@Slf4j
public class ResumeServiceImpl implements ResumeService {
    
    public void parseResume(Long resumeId) {
        log.info("开始解析简历, resumeId={}", resumeId);
        try {
            // 解析逻辑
            log.info("简历解析完成, resumeId={}, status=success", resumeId);
        } catch (Exception e) {
            log.error("简历解析失败, resumeId={}", resumeId, e);
            throw e;
        }
    }
}
```

### 12.2 切面日志（API 调用记录）

```java
@Aspect
@Component
@Slf4j
public class ApiLogAspect {
    
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logApi(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();
        
        log.info("请求开始: method={}, args={}", method, 
                Arrays.toString(pjp.getArgs()));
        
        try {
            Object result = pjp.proceed();
            long cost = System.currentTimeMillis() - start;
            log.info("请求完成: method={}, cost={}ms", method, cost);
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("请求异常: method={}, cost={}ms", method, cost, e);
            throw e;
        }
    }
}
```

### 12.3 AI 调用日志

```java
// MyLogAdvisor.java
@Slf4j
public class MyLogAdvisor implements AroundAdvisor {
    
    @Override
    public AdvisedResponse around(AdvisedRequest request) {
        long start = System.currentTimeMillis();
        
        AdvisedResponse response = proceed(request);
        
        long cost = System.currentTimeMillis() - start;
        int inputTokens = ...;  // 从 response 获取
        int outputTokens = ...;
        
        log.info("AI调用完成: model={}, cost={}ms, inputTokens={}, outputTokens={}",
                "deepseek-chat", cost, inputTokens, outputTokens);
        
        return response;
    }
}
```

---

## 13. 测试策略

### 13.1 测试层级

| 层级 | 覆盖范围 | 示例 |
|------|---------|------|
| 单元测试 | Service 核心逻辑 | AuthService 注册/登录逻辑 |
| 单元测试 | 工具类 | JsonUtil、TraceIdUtil |
| 集成测试 | 数据库操作 | ResumeRepository CRUD |
| 集成测试 | RAG 链路 | 简历向量化 → 检索 |
| API 测试 | 接口响应 | 注册接口参数校验 |
| AI 测试 | Prompt 效果 | 评估报告输出格式验证 |

### 13.2 关键测试用例

```java
// AuthServiceTest.java
@SpringBootTest
class AuthServiceTest {
    
    @Autowired
    private AuthService authService;
    
    @Test
    void testRegister_Success() {
        var request = new RegisterRequest("test@test.com", "password123");
        var response = authService.register(request);
        assertNotNull(response.getAccessToken());
        assertEquals("test@test.com", response.getUser().getEmail());
    }
    
    @Test
    void testRegister_DuplicateEmail() {
        var request = new RegisterRequest("test@test.com", "password123");
        authService.register(request);
        assertThrows(BusinessException.class, () -> {
            authService.register(request);
        });
    }
    
    @Test
    void testLogin_WrongPassword() {
        var request = new LoginRequest("test@test.com", "wrongpassword");
        assertThrows(BusinessException.class, () -> {
            authService.login(request);
        });
    }
}

// ReportServiceTest.java
@SpringBootTest
class ReportServiceTest {
    
    @Test
    void testReport_JsonOutput() {
        var report = reportService.generateReport(1L, 1L);
        assertNotNull(report.getMatchScore());
        assertNotNull(report.getTechStackAnalysis());
        assertFalse(report.getHighlights().isEmpty());
        assertFalse(report.getWeaknesses().isEmpty());
        // 验证匹配度在合理范围
        assertTrue(report.getMatchScore() >= 0 && report.getMatchScore() <= 100);
    }
}
```

---

## 14. 开发规范

### 14.1 代码规范

- 遵循阿里巴巴 Java 开发手册
- 统一使用 Lombok（@Data, @Slf4j, @Builder）
- 禁止 System.out.println，使用 @Slf4j
- 方法参数使用 @NotNull、@NotEmpty 等注解
- 类名、方法名使用驼峰命名

### 14.2 Git 提交规范

```
feat: 新功能
fix: 修复 bug
refactor: 重构
docs: 文档
test: 测试
chore: 构建/工具
```

### 14.3 开发流程

```
1. 从 main 拉取 feature 分支
2. 开发完成后本地测试
3. 提交 PR 到 main
4. CI 自动构建 + 测试
5. 合并到 main 后自动部署
```

---

## 15. 面试展示话术

| 技术点 | 面试话术 |
|--------|---------|
| **RAG 全链路** | 从 PDF 解析 → 切片 → 向量化 → 检索增强 → 生成，全链路自研，不依赖平台拖拽，每个环节都可控 |
| **Agent 编排** | 基于 Spring AI Advisor Chain 设计管线，可插拔、可扩展，定义了安全过滤、记忆注入、RAG 检索的执行顺序 |
| **SSE 流式** | 面试对话流式输出，前端 EventSource 逐字渲染，断线自动重连 |
| **数据隔离** | 多用户场景下，向量检索按 user_id 过滤，确保用户只能搜到自己的简历内容，不越权 |
| **面试状态机** | 三面制状态机，支持中断恢复（1小时内）、过期自动失效、主动结束生成总结 |
| **结构化输出** | LLM 返回 JSON 格式，精确解析匹配度、技术栈、评分等字段，前端按字段渲染 |
| **模块化架构** | 遵循 DDD 限界上下文划分，AI 核心模块（aicore）可独立抽取为微服务 |
| **工程规范** | Liquibase 版本管理、统一响应体、全局异常处理、traceId 链路追踪、多环境配置 |
| **部署方案** | Docker Compose 容器化编排，MinIO 对象存储，Nginx 反向代理，GitHub Actions CI/CD |

---

## 16. 附录

### 16.1 服务器初始化脚本

```bash
#!/bin/bash
# 服务器初始化脚本

# 1. 安装 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 2. 安装 Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 3. 创建项目目录
sudo mkdir -p /opt/offer-agent
sudo mkdir -p /opt/offer-agent/offer-agent-frontend

# 4. 配置环境变量
cat > /opt/offer-agent/.env << EOF
MYSQL_ROOT_PASSWORD=your_secure_password
POSTGRES_PASSWORD=your_secure_password
DEEPSEEK_API_KEY=your_deepseek_key
BAILIAN_API_KEY=your_bailian_key
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=your_minio_secret
EOF

# 5. 设置目录权限
sudo chown -R $USER:$USER /opt/offer-agent
```

### 16.2 本地开发环境

```bash
# 启动基础设施
docker compose up -d mysql postgres minio

# 启动后端（IDE 中运行 OfferAgentApplication.java）

# 启动前端
cd offer-agent-frontend
npm install
npm run dev
```

---

> 文档结束。如有疑问，按 PRD 决策为准，技术细节可调整。