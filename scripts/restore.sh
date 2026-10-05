#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/lib.sh"
[[ $# -ge 1 && $# -le 2 ]] || fail '用法：bash scripts/restore.sh backups/database-xxx.sql.gz --confirm'
[[ "${2:-}" == '--confirm' ]] || fail '恢复会覆盖当前库中的同名表。确认后添加 --confirm；脚本先保存当前数据库备份。'
RESTORE_FILE="$(realpath -- "$1")"
[[ -f "$RESTORE_FILE" && "$RESTORE_FILE" == *.sql.gz ]] || fail '请选择存在的 .sql.gz 数据库备份。'
require_docker
[[ -f "$PROJECT_ROOT/.env" ]] || fail '缺少 .env。'
gzip -t "$RESTORE_FILE"
if [[ -f "$RESTORE_FILE.sha256" ]]; then
  (cd "$(dirname -- "$RESTORE_FILE")" && sha256sum --check "$(basename -- "$RESTORE_FILE").sha256")
fi
compose up -d --wait --wait-timeout 240 mysql
bash "$PROJECT_ROOT/scripts/backup.sh"
compose stop frontend backend
trap 'printf "%s\n" "恢复未完成时请保留停机状态，检查错误并用升级前备份重试。" >&2' ERR
gzip -dc "$RESTORE_FILE" | compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user="$MYSQL_USER" --default-character-set=utf8mb4 "$MYSQL_DATABASE"'
# Preserve HTTPS configuration if the currently created frontend container uses it.
compose start backend frontend
for (( attempt = 1; attempt <= 60; attempt++ )); do
  if compose exec -T backend curl --fail --silent http://localhost:8080/actuator/health >/dev/null 2>&1; then
    printf '%s\n' '恢复完成，后端健康检查已通过。请执行交易验收。'
    exit 0
  fi
  sleep 3
done
fail '恢复 SQL 已完成，但后端健康检查超时，请检查 docker compose logs backend。'
