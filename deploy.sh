#!/usr/bin/env bash
# ============================================
# OfferPilot（面壁）一键部署脚本
# 用法: bash deploy.sh [命令]
#    deploy.sh up        — 首次部署启动
#    deploy.sh rebuild   — 重新构建并启动（代码更新后）
#    deploy.sh down      — 停止并删除容器
#    deploy.sh logs      — 查看日志
#    deploy.sh ps        — 查看容器状态
# ============================================

set -euo pipefail

APP_NAME="OfferPilot"
COMPOSE_FILE="docker-compose.yml"
ENV_FILE=".env"

cd "$(dirname "$0")"

# 检查 .env 文件
if [ ! -f "$ENV_FILE" ]; then
    if [ -f ".env.example" ]; then
        echo "[!] 未发现 .env 文件，正在从 .env.example 创建..."
        cp .env.example .env
        echo "[!] 请编辑 .env 文件填入 DEEPSEEK_API_KEY 后重新运行"
        exit 1
    else
        echo "[!] 缺少 .env 文件，请创建后重试"
        exit 1
    fi
fi

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "[!] 请先安装 Docker"
    exit 1
fi

case "${1:-up}" in
    up)
        echo ">>> 启动 $APP_NAME 基础设施..."
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d postgres minio
        echo ">>> 等待数据库就绪（约 30 秒）..."
        sleep 30
        echo ">>> 启动 $APP_NAME 后端..."
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d backend
        echo ">>> 部署完成！"
        echo "    API:      http://$(hostname -I | awk '{print $1}'):8080"
        echo "    MinIO:    http://$(hostname -I | awk '{print $1}'):9001"
        echo "    健康检查: curl http://localhost:8080/api/health"
        ;;
    rebuild)
        echo ">>> 重新构建 $APP_NAME..."
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" build backend
        echo ">>> 重启服务..."
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d backend
        echo ">>> 更新完成！"
        ;;
    down)
        echo ">>> 停止 $APP_NAME..."
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down
        echo ">>> 已停止"
        ;;
    logs)
        echo ">>> 查看日志（Ctrl+C 退出）..."
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" logs -f
        ;;
    ps)
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps
        ;;
    *)
        echo "用法: bash deploy.sh [命令]"
        echo "   up        — 首次部署启动"
        echo "   rebuild   — 重新构建并启动（代码更新后）"
        echo "   down      — 停止并删除容器"
        echo "   logs      — 查看日志"
        echo "   ps        — 查看容器状态"
        exit 1
        ;;
esac