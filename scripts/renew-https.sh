#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/lib.sh"
require_docker
DOMAIN_FILE="$PROJECT_ROOT/deploy/certs/domain.txt"
[[ -s "$DOMAIN_FILE" ]] || fail '缺少证书域名记录，请先运行 enable-https.sh。'
DOMAIN="$(cat "$DOMAIN_FILE")"
[[ "$DOMAIN" =~ ^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?\.[a-zA-Z]{2,}$ && "$DOMAIN" != *..* ]] || fail '证书域名记录格式错误。'
docker run --rm \
  -v "$PROJECT_ROOT/deploy/letsencrypt:/etc/letsencrypt" \
  -v "$PROJECT_ROOT/deploy/acme:/var/www/certbot" \
  certbot/certbot:latest renew --webroot --webroot-path /var/www/certbot --non-interactive --quiet
CERT_SOURCE="$PROJECT_ROOT/deploy/letsencrypt/live/$DOMAIN"
[[ -s "$CERT_SOURCE/fullchain.pem" && -s "$CERT_SOURCE/privkey.pem" ]] || fail '证书文件缺失。'
if ! cmp -s "$CERT_SOURCE/fullchain.pem" "$PROJECT_ROOT/deploy/certs/fullchain.pem"; then
  umask 077
  cp -L -- "$CERT_SOURCE/fullchain.pem" "$PROJECT_ROOT/deploy/certs/fullchain.pem"
  cp -L -- "$CERT_SOURCE/privkey.pem" "$PROJECT_ROOT/deploy/certs/privkey.pem"
  chmod 600 "$PROJECT_ROOT/deploy/certs/privkey.pem"
  chmod 644 "$PROJECT_ROOT/deploy/certs/fullchain.pem"
  select_https
  compose exec -T frontend nginx -t
  compose exec -T frontend nginx -s reload
  printf '%s\n' '证书已更新并加载。'
fi
