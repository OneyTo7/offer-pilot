# 面壁 (OfferPilot) — 宝塔面板部署指南

## 服务器环境

- **服务器**：`your-server-ip`（建议 2C4G 及以上）
- **系统**：CentOS / Ubuntu（宝塔面板已安装）
- **域名**：暂无，先用 IP 访问

## 前置条件（已在服务器运行）

- PostgreSQL 16 + pgvector 扩展（Docker 容器，端口 5432）
- MinIO 对象存储（Docker 容器，端口 19000）
- 宝塔面板已安装 Nginx

## 部署步骤

### 1. 后端部署

#### 1.1 本地构建

```bash
cd offer-pilot
JAVA_HOME=/path/to/jdk-21 ./mvnw package -DskipTests -B
```

在 `target/` 目录下得到 `offer-pilot.jar`（约 80MB）。

#### 1.2 宝塔上传

1. 打开宝塔面板 → 文件 → 上传 `offer-pilot.jar` 到 `/opt/offer-pilot/`
2. 上传 `application-prod.yaml`（或修改后的配置）到 `/opt/offer-pilot/`

#### 1.3 创建 Java 项目

1. 宝塔 → 软件商店 → Java 项目管理器 → 添加 Java 项目
2. 项目类型：Spring Boot
3. **Jar 路径**：`/opt/offer-pilot/offer-pilot.jar`
4. **端口**：`8080`
5. **JDK**：选择 Java 21
6. **项目执行命令**：

```bash
java -jar /opt/offer-pilot/offer-pilot.jar \
  --spring.profiles.active=prod \
  --server.port=8080 \
  -Dspring.ai.deepseek.api-key=${DEEPSEEK_API_KEY}
```

7. **环境变量**（在宝塔 Java 项目设置中配置）：

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `DEEPSEEK_API_KEY` | DeepSeek API Key | `sk-xxxx` |
| `POSTGRES_PASSWORD` | PostgreSQL 密码 | `pgpass` |
| `MINIO_ACCESS_KEY` | MinIO 访问密钥 | `minio` |
| `MINIO_SECRET_KEY` | MinIO 密钥 | `xxxx` |
| `SA_TOKEN_JWT_SECRET` | JWT 签名密钥（64 位 hex） | `openssl rand -hex 32` 生成 |
| `API_KEY_ENCRYPTION_SECRET` | API Key 加密密钥（64 位 hex） | `openssl rand -hex 32` 生成 |

> **重要**：生产环境务必设置 `SA_TOKEN_JWT_SECRET` 和 `API_KEY_ENCRYPTION_SECRET` 为随机值，不要使用默认值。

#### 1.4 后端数据源配置

后端连接远程 PostgreSQL 使用 `application-prod.yaml` 中的配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/offer_pilot
    username: postgres
    password: ${POSTGRES_PASSWORD}
```

如果 PostgreSQL 在 Docker 中运行，且后端在宿主机上直接运行（非 Docker），请使用 `localhost` 或 `127.0.0.1` 连接。

### 2. 前端部署

#### 2.1 本地构建

```bash
cd offer-pilot-ui
npm install
npm run build
```

在 `dist/` 目录得到静态文件。

#### 2.2 宝塔上传

1. 宝塔 → 网站 → 添加站点
2. **域名**：填写服务器 IP 或域名
3. **根目录**：`/www/wwwroot/offer-pilot`
4. 上传 `dist/` 目录下所有文件到该目录

#### 2.3 Nginx 配置

在宝塔网站设置 → 配置文件中，参考 `nginx.conf` 文件进行设置：

- 将 `/api/` 反代到 `http://127.0.0.1:8080`
- 确保 `proxy_buffering off;` 以支持 SSE 流式反馈
- 设置 `client_max_body_size 10m;` 以支持文件上传

### 3. 验证

#### 3.1 健康检查

```bash
curl http://localhost:8080/api/health
```

预期返回：`{"code":200,"message":"OK","data":"UP","trace_id":"..."}`

#### 3.2 核心链路验证

1. 访问 `http://服务器IP` 打开前端页面
2. 注册账号 → 登录
3. 上传简历 → 创建目标职位 → 生成评估报告
4. 创建面试 → 答题 → 查看 SSE 流式反馈
5. 知识库 → 上传 Markdown 文件 → 查看分片

### 4. 注意事项

- **内存**：2C4G 服务器若资源不足，可关闭 ONNX 重排器（`RERANKER_MODEL_URI` 设为空或不配置）
- **日志**：后端日志在宝塔 Java 项目管理器的日志标签页中查看
- **备份**：定期备份 PostgreSQL 数据库
- **HTTPS**：建议后续配置域名 + 宝塔 SSL 证书

## 常见问题

### Q: 启动后 502 Bad Gateway
检查后端是否启动成功（`java -jar` 日志），确认 Nginx 反代地址是否正确。

### Q: 面试 SSE 流式反馈不显示
确认 Nginx 配置中有 `proxy_buffering off;`，且 `proxy_read_timeout` 不少于 300s。

### Q: 文件上传失败
检查 `client_max_body_size` 配置，知识库文件上限 5MB。

### Q: 向量检索无结果
确认 `vector_store` 表是否存在，知识库文档是否已索引（状态为"已完成"）。