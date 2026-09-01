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
- **Frontend:** Vue 3 + TypeScript + Vite + Element Plus (separate repo)
- **Auth:** Sa-Token + JWT (dual-token: 2h access + 7d refresh)
- **File storage:** MinIO (object storage)
- **Document parsing:** 阿里云百炼 (Alibaba Cloud Bailian)
- **DB migrations:** Liquibase

## Commands

```bash
# Build (skip tests for fast compile)
./mvnw package -DskipTests -B

# Build with tests
./mvnw package -B

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=OfferPilotApplicationTests

# Run a single test method
./mvnw test -Dtest=OfferPilotApplicationTests#contextLoads

# Run the application
./mvnw spring-boot:run

# Run with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Start infrastructure (MySQL, PostgreSQL, MinIO) via Docker Compose
# (docker-compose.yml is planned but not yet created)
```

## Architecture

### Module Structure (planned, following DDD bounded contexts)

```
com.eyki.offeragent/
├── OfferAgentApplication.java
├── common/          # Shared: config, constants, exceptions, ApiResult, PageResult, utils
├── auth/            # Authentication: register/login, Sa-Token, JWT
├── resume/          # Resume: upload, PDF parsing via Bailian, structured storage
├── position/        # Target position: CRUD, JD management
├── report/          # Assessment report: AI-generated match analysis
├── interview/       # Mock interview: 3-round session, SSE streaming, state machine
├── aicore/          # AI core: Advisor Chain, RAG, memory, prompts
│   ├── advisor/     # SafeValidAdvisor, ReReadingAdvisor, MyLogAdvisor
│   ├── rag/         # RagService, TokenTextSplitter, QueryAugmenterConfig
│   ├── memory/      # InterviewChatMemory, ChatMemoryStore
│   ├── prompt/      # ReportPrompt, InterviewPrompt
│   └── service/     # AiService (LLM invocation wrapper)
└── storage/         # File storage: MinIO integration
```

### Key Architectural Decisions

1. **Advisor Chain for AI pipeline:** SafeValidAdvisor → ReReadingAdvisor →
   MessageChatMemoryAdvisor → (optional) RetrievalAugmentationAdvisor → LLM → output validation
2. **RAG with user-level isolation:** All vector store entries tagged with `user_id`; search filters
   by `user_id` to prevent data leakage
3. **Interview state machine:** 3 rounds, 10 Qs each, interruptible (1-hour resume window),
   auto-expire after 1h
4. **Rate limiting:** Free tier = 3 reports/day + 1 interview/day; users can supply their own
   DeepSeek API key for unlimited use
5. **SSE streaming:** Interview answer responses streamed via `text/event-stream`; frontend uses
   EventSource with auto-reconnect
6. **Unified response:** `ApiResult<T>` (code, message, data, traceId) and `PageResult<T>` (records,
   total, page, size)
7. **Error codes:** 200=success, 400/401/403/404/409/429/500 + custom codes (1001=AI error,
   1002=parse error, 2001=interview expired)

### API Design

Base path: `/api/v1`

| Module    | Endpoints                                                                                                                                                                    |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Auth      | `POST /register`, `/login`, `/refresh`, `/logout`, `GET /me`, `PUT /profile`, `PUT /api-key`, `DELETE /api-key`                                                              |
| Resume    | `POST /resumes`, `GET /resumes`, `GET /resumes/{id}`, `DELETE /resumes/{id}`, `PUT /resumes/{id}/default`                                                                    |
| Position  | `POST /positions`, `GET /positions`, `GET /positions/{id}`, `DELETE /positions/{id}`, `PUT /positions/{id}/default`                                                          |
| Report    | `POST /reports`, `GET /reports`, `GET /reports/{id}`, `DELETE /reports/{id}`                                                                                                 |
| Interview | `POST /interviews`, `GET /interviews`, `GET /interviews/{id}`, `POST /{id}/start-round`, `POST /{id}/answer` (SSE), `POST /{id}/skip`, `POST /{id}/end`, `GET /{id}/summary` |
| AI        | `GET /ai/info`                                                                                                                                                               |
| File      | `GET /files/{filePath}`                                                                                                                                                      |
| Health    | `GET /api/health`                                                                                                                                                            |

### Security

- **Data isolation**: All queries filtered by `user_id`; every service operation validates ownership before access
- **API Key encryption**: AES-256-GCM encryption via `AesGcmEncryptor`; key derived from env var `API_KEY_ENCRYPTION_SECRET`
- **Token usage**: Tracked per user per day in `user_token_usage` table; free tier capped at 100K tokens/month
- **Password**: BCrypt hashing; dual-token auth (2h access + 7d refresh)

### Database (7 tables via Liquibase)

- `users` — email/password auth, optional api_key
- `resumes` — parsed text, tech_stack, work_years, education, status
- `target_positions` — JD, company, salary
- `reports` — match_score, tech_stack_analysis, highlights, weaknesses (JSON)
- `interview_sessions` — state machine: current_round, current_question, status, expired_at
- `interview_questions` — per-question records: text, answer, feedback, score, status

### Deployment

- Docker Compose: MySQL 8 + pgvector(pg16) + Redis 7 + MinIO + backend + frontend
- Server: 火山引擎 2C4G
- CI/CD: GitHub Actions (build → SCP → docker compose up -d --build)

## Current State

This is a **new project** — the architecture docs are fully written, but only the
`OfferPilotApplication` bootstrap class and context-loads test exist in code. The actual module
structure, services, controllers, and infrastructure are yet to be implemented.