#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/lib.sh"
require_docker
[[ -f "$PROJECT_ROOT/.env" ]] || fail '缺少 .env，请先部署。'
[[ -n "$(compose ps --status running -q mysql)" ]] || fail 'MySQL 容器未运行，备份中止。'
RETENTION="$(backup_retention)"
BACKUP_DIR="$PROJECT_ROOT/backups"
mkdir -p -- "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
BACKUP_FILE="$BACKUP_DIR/database-$(date -u +%Y%m%dT%H%M%SZ)-$$.sql.gz"
TEMP_FILE="$BACKUP_FILE.partial"
trap 'rm -f -- "$TEMP_FILE"' EXIT
umask 077
compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --user="$MYSQL_USER" --single-transaction --no-tablespaces --default-character-set=utf8mb4 "$MYSQL_DATABASE"' | gzip > "$TEMP_FILE"
gzip -t "$TEMP_FILE"
mv -- "$TEMP_FILE" "$BACKUP_FILE"
if command -v sha256sum >/dev/null; then
  (cd "$BACKUP_DIR" && sha256sum "$(basename -- "$BACKUP_FILE")" > "$(basename -- "$BACKUP_FILE").sha256")
fi
find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'database-*.sql.gz' -o -name 'database-*.sql.gz.sha256' \) -mtime "+$((RETENTION - 1))" -delete
printf '备份已保存：%s\n' "$BACKUP_FILE"
