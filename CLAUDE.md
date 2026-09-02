# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Project Overview

**面壁 (OfferPilot)** — AI简历智能平台 for tech job seekers. Upload a resume, set a target position,
and get an AI-generated match assessment + 3-round mock interview (10 questions each round).

- **Framework:** Spring Boot 4.1.1 + Spring AI 2.0.1
- **Language:** Java 21 (Preview features enabled)
- **Build:** Maven (wrapped at `./mvnw`)
- **LLM:** DeepSeek (OpenAI-compatible API)
- **Vector DB:** PostgreSQL + pgvector (512-dim, BGE bge-small-zh-v1.5)
- **Frontend:** Vue 3 + TypeScript + Vite + Element Plus (separate repo: `offer-pilot-ui`)
- **Auth:** Sa-Token + JWT (dual-token: 2h access + 7d in-memory refresh)
- **File storage:** MinIO (object storage, Docker container)
- **Document parsing:** 阿里云百炼 (Alibaba Cloud Bailian)
- **DB migrations:** Liquibase (PostgreSQL)

## Commands

```bash
JAVA_HOME=/Users/oliver/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home ./mvnw <cmd>
# Build (skip tests for fast compile)
./mvnw package -DskipTests -B

# Build with tests
./mvnw package -B

# Run all unit tests
./mvnw test -Dtest=KnowledgeEtlServiceTest,RagServiceTest,MinioFileStorageTest

# Run the application (dev profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd /Users/oliver/IdeaProjects/offer-pilot-ui
npm run dev        # Dev server on port 3000
npm run build      # Production build to dist/
npx vue-tsc -b     # Type check
```

## Architecture

### Module Structure

```
com.eyki.offerpilot/
├── OfferPilotApplication.java
├── common/          # Shared: config, constants, exceptions, ApiResult, utils
├── auth/            # Authentication: register/login, Sa-Token, JWT, API key management
├── resume/          # Resume: upload, PDF parsing via Bailian, structured storage
├── position/        # Target position: CRUD, JD management
├── report/          # Assessment report: AI-generated match analysis
├── interview/       # Mock interview: 3-round session, SSE streaming, state machine
├── aicore/          # AI core: Advisor Chain, RAG, memory, prompts
│   ├── advisor/     # SafeValidAdvisor, ReReadingAdvisor, MyLogAdvisor, TokenUsageAdvisor, ApiKeyRoutingAdvisor
│   ├── rag/         # RagService, KnowledgeEtlService, BgeCrossEncoderReRanker
│   ├── memory/      # PgChatMemory (PostgreSQL-backed chat history)
│   ├── prompt/      # ReportPrompt, InterviewPrompt, QuestionFeedbackPrompt
│   ├── usage/       # UserTokenUsageService (token tracking and rate limiting)
│   └── service/     # AiService (LLM invocation wrapper)
├── knowledge/       # Knowledge base: document upload, Markdown/TXT parsing, vector indexing
└── storage/         # File storage: MinIO integration
```

### Key Architectural Decisions

1. **Advisor Chain for AI pipeline:** SafeValidAdvisor(0) → TokenUsageAdvisor(0) → ReReadingAdvisor(1) →
   MessageChatMemoryAdvisor(2) → RetrievalAugmentationAdvisor(3) → MyLogAdvisor(4) → ApiKeyRoutingAdvisor(5)
2. **Dual ChatClient**: `chatClient` (with RAG, for interview/report) and `chatClientNoRag` (without RAG, for resume parsing)
3. **RAG with user-level isolation:** All vector store entries tagged with `user_id`; search filters by `user_id`
4. **User API Key routing:** Users can supply their own DeepSeek API key → `ApiKeyRoutingAdvisor` intercepts model call
5. **Interview state machine:** 3 rounds, 10 Qs each, SSE streaming for answer feedback
6. **Rate limiting:** Free tier = 3 reports/day + 1 interview/day + 100K tokens/month
7. **SSE streaming:** Interview answer responses streamed via `text/event-stream`; dedicated virtual-thread executor
8. **Unified response:** `ApiResult<T>` (code, message, data, traceId)
9. **Error codes:** 200=success, 400/401/403/404/409/429/500 + custom codes (1001=AI error, 1002=parse error, 2001=interview expired)

### API Design

Base path: `/api/v1`

| Module    | Endpoints                                                                                                                                                                    |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Auth      | `POST /register`, `/login`, `/refresh`, `/logout`, `GET /me`, `PUT /profile`, `PUT /api-key`, `DELETE /api-key`                                                              |
| Resume    | `POST /resumes`, `GET /resumes`, `GET /resumes/{id}`, `DELETE /resumes/{id}`, `PUT /resumes/{id}/default`                                                                    |
| Position  | `POST /positions`, `GET /positions`, `GET /positions/{id}`, `DELETE /positions/{id}`, `PUT /positions/{id}/default`                                                          |
| Report    | `POST /reports`, `GET /reports`, `GET /reports/{id}`, `DELETE /reports/{id}`                                                                                                 |
| Interview | `POST /interviews`, `GET /interviews`, `GET /interviews/{id}`, `POST /{id}/start-round`, `POST /{id}/answer` (SSE), `POST /{id}/skip`, `POST /{id}/end`, `GET /{id}/summary` |
| Knowledge | `POST /knowledge`, `POST /knowledge/upload`, `GET /knowledge`, `GET /knowledge/{id}`, `GET /knowledge/{id}/chunks`, `DELETE /knowledge/{id}`, `POST /knowledge/search`        |
| AI        | `GET /ai/info`                                                                                                                                                                |
| File      | `GET /files/{filePath}`                                                                                                                                                       |
| Health    | `GET /api/health`                                                                                                                                                             |

### Security

- **Data isolation**: All queries filtered by `user_id`; every service operation validates ownership before access
- **API Key encryption**: AES-256-GCM encryption via `AesGcmEncryptor`; key from env var `API_KEY_ENCRYPTION_SECRET`
- **Token usage**: Tracked per user per day in `user_token_usage` table; free tier capped at 100K tokens/month
- **Password**: BCrypt hashing; Sa-Token dual-token auth (2h access + 7d refresh via in-memory store)

### Database (tables via Liquibase)

- `users` — email/password auth, optional encrypted api_key
- `resumes` — parsed text, tech_stack, work_years, education, status
- `target_positions` — JD, company, salary
- `reports` — match_score, tech_stack_analysis, highlights, weaknesses (JSON)
- `interview_sessions` — state machine: current_round, current_question, status, expired_at
- `interview_questions` — per-question records: text, answer, feedback, score, status
- `knowledge_base` — knowledge documents: title, content, content_type, status
- `user_token_usage` — daily LLM token usage tracking
- `chat_memory` — conversation history (PgChatMemory)
- `vector_store` — pgvector table (auto-created by Spring AI)

### Deployment

- **Production server**: 火山引擎 2C4G, 宝塔面板
- **Backend**: Spring Boot jar managed by 宝塔 Java 项目管理器
- **Frontend**: Vue 3 SPA, served by 宝塔 Nginx (see `offer-pilot-ui/nginx.conf`)
- **Database**: PostgreSQL 16 + pgvector via Docker
- **File storage**: MinIO via Docker
- **Deployment guide**: `DEPLOY.md`

## Current State

This is a fully functional MVP with all core features implemented: resume parsing, position management, AI match reports, 3-round mock interviews with SSE streaming, knowledge base with vector search, and user API key support. The codebase is being prepared for open source release.