#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
COMPOSE_FILES=(-f "$PROJECT_ROOT/docker-compose.yml")

fail() { printf '%s\n' "$*" >&2; exit 1; }

require_docker() {
  command -v docker >/dev/null || fail '请先安装 Docker Engine / Docker Desktop 和 Compose v2。'
  docker info >/dev/null 2>&1 || fail 'Docker 未启动，或当前账号没有访问权限。'
  docker compose version >/dev/null || fail '需要 Docker Compose v2（docker compose）。'
}

compose() { docker compose --env-file "$PROJECT_ROOT/.env" "${COMPOSE_FILES[@]}" "$@"; }

random_secret() {
  if command -v openssl >/dev/null; then
    openssl rand -hex 32
  else
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n'
  fi
}

initialize_environment() {
  if [[ ! -f "$PROJECT_ROOT/.env" ]]; then
    (
      umask 077
      set -o noclobber
      cat > "$PROJECT_ROOT/.env" <<EOF
DB_NAME=auto_delivery
DB_USER=autodelivery
DB_PASSWORD=$(random_secret)
DB_ROOT_PASSWORD=$(random_secret)
JWT_SECRET=$(random_secret)
ADMIN_USERNAME=admin
ADMIN_PASSWORD=$(random_secret)
APP_SEED_DEMO=false
REDIS_ENABLED=true
APP_BASE_URL=http://localhost
HTTP_PORT=80
HTTPS_PORT=443
BACKUP_RETENTION_DAYS=7
EOF
    )
    printf '%s\n' '已创建私密 .env，管理员初始密码由随机数生成。请在服务器本地查看并登录后修改，脚本不输出密钥。'
  fi
  if grep -Eq '^([A-Z_]+)=.*CHANGE_ME' "$PROJECT_ROOT/.env"; then
    fail '.env 仍含示例占位值。请填写，或移走未使用的示例文件后重新部署。'
  fi
  compose config --quiet
}

select_https() {
  [[ -s "$PROJECT_ROOT/deploy/certs/fullchain.pem" ]] || fail '缺少 deploy/certs/fullchain.pem。'
  [[ -s "$PROJECT_ROOT/deploy/certs/privkey.pem" ]] || fail '缺少 deploy/certs/privkey.pem。'
  COMPOSE_FILES+=(-f "$PROJECT_ROOT/deploy/docker-compose.https.yml")
}

backup_retention() {
  local retention
  retention="${BACKUP_RETENTION_DAYS:-$(sed -n 's/^BACKUP_RETENTION_DAYS=//p' "$PROJECT_ROOT/.env" | tail -1)}"
  retention="${retention:-7}"
  [[ "$retention" =~ ^[0-9]+$ ]] && (( retention >= 7 )) || fail 'BACKUP_RETENTION_DAYS 必须是至少 7 的整数。'
  printf '%s' "$retention"
}
