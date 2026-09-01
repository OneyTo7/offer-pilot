# 贡献指南

感谢你考虑为 面壁 (OfferPilot) 贡献代码！

## 开发环境

### 前置条件

- JDK 21+
- Docker & Docker Compose（用于启动基础设施）
- Node.js 18+（前端开发）

### 快速开始

```bash
# 1. 克隆仓库
git clone https://github.com/your-username/offer-pilot.git
cd offer-pilot

# 2. 启动基础设施
docker compose up -d

# 3. 启动后端
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4. 启动前端（新终端）
cd offer-pilot-ui
npm install
npm run dev
```

## 代码规范

- 后端遵循阿里巴巴 Java 开发手册
- 使用 Lombok 减少样板代码，禁止 `System.out.println`
- 统一使用 `@Slf4j` 记录日志
- 所有 Controller 类需添加类级 Javadoc
- 所有 Service 接口需添加方法级 Javadoc

## 提交规范

提交信息格式：

```
<type>: <简短描述>

<详细说明（可选）>
```

类型：

- `feat` — 新功能
- `fix` — 修复 bug
- `refactor` — 重构
- `docs` — 文档
- `test` — 测试
- `chore` — 杂项

示例：

```
feat: 添加用户自配 API Key 功能
fix: 修复异步线程中 Sa-Token 上下文丢失问题
```

## 分支策略

- `main` — 稳定分支，保持可发布状态
- `feat/*` — 功能分支，从 main 拉出
- `fix/*` — 修复分支

## 测试

- 核心 Service 层必须包含单元测试
- 运行所有测试：`./mvnw test -B`
- 单个测试：`./mvnw test -Dtest=ClassName#methodName`

## PR 流程

1. 从 main 创建功能分支
2. 实现功能 + 添加测试
3. 确保 `./mvnw package -B` 通过
4. 提交 PR 到 main
5. 等待 Review

## 问题反馈

- 提交 Issue 时请包含：
  - 复现步骤
  - 期望行为 vs 实际行为
  - 日志或截图（如有）
  - 环境信息（JDK 版本、OS）