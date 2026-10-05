#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/lib.sh"

HTTPS=false
for argument in "$@"; do
  case "$argument" in
    --https) HTTPS=true ;;
    *) fail '用法：bash scripts/deploy.sh [--https]' ;;
  esac
done

require_docker
initialize_environment
if "$HTTPS"; then select_https; fi
compose config --quiet
printf '%s\n' '构建应用镜像……'
compose build --pull

printf '%s\n' '启动数据库，并在应用更新前备份（首次部署也保存初始库）……'
compose up -d --wait --wait-timeout 240 mysql
bash "$PROJECT_ROOT/scripts/backup.sh"

printf '%s\n' '启动服务并等待健康检查……'
compose up -d --wait --wait-timeout 300
compose exec -T frontend nginx -t
printf '%s\n' '部署完成。请确认 .env 的 APP_BASE_URL 与实际地址一致，并通过浏览器执行交易验收。'
compose ps
