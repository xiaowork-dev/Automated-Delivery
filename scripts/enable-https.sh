#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/lib.sh"
[[ $# == 2 ]] || fail '用法：bash scripts/enable-https.sh shop.example.com admin@example.com'
DOMAIN="$1"
EMAIL="$2"
[[ "$DOMAIN" =~ ^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?\.[a-zA-Z]{2,}$ && "$DOMAIN" != *..* ]] || fail '请填写已解析到本服务器的有效域名。'
[[ "$EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] || fail '请填写证书通知邮箱。'
require_docker
initialize_environment
mkdir -p "$PROJECT_ROOT/deploy/letsencrypt" "$PROJECT_ROOT/deploy/acme" "$PROJECT_ROOT/deploy/certs"
# HTTP-01 validation needs the public port 80 and DNS pointing to this server.
export HTTP_PORT=80 HTTPS_PORT=443
bash "$PROJECT_ROOT/scripts/deploy.sh"
docker run --rm \
  -v "$PROJECT_ROOT/deploy/letsencrypt:/etc/letsencrypt" \
  -v "$PROJECT_ROOT/deploy/acme:/var/www/certbot" \
  certbot/certbot:latest certonly --webroot --webroot-path /var/www/certbot \
  --non-interactive --agree-tos --email "$EMAIL" --cert-name "$DOMAIN" -d "$DOMAIN"
CERT_SOURCE="$PROJECT_ROOT/deploy/letsencrypt/live/$DOMAIN"
[[ -s "$CERT_SOURCE/fullchain.pem" && -s "$CERT_SOURCE/privkey.pem" ]] || fail '证书签发结果缺失。'
umask 077
cp -L -- "$CERT_SOURCE/fullchain.pem" "$PROJECT_ROOT/deploy/certs/fullchain.pem"
cp -L -- "$CERT_SOURCE/privkey.pem" "$PROJECT_ROOT/deploy/certs/privkey.pem"
chmod 600 "$PROJECT_ROOT/deploy/certs/privkey.pem"
chmod 644 "$PROJECT_ROOT/deploy/certs/fullchain.pem"
printf '%s\n' "$DOMAIN" > "$PROJECT_ROOT/deploy/certs/domain.txt"
sed -i "s|^APP_BASE_URL=.*|APP_BASE_URL=https://$DOMAIN|; s|^HTTP_PORT=.*|HTTP_PORT=80|; s|^HTTPS_PORT=.*|HTTPS_PORT=443|" "$PROJECT_ROOT/.env"
bash "$PROJECT_ROOT/scripts/deploy.sh" --https
printf 'HTTPS 已配置： https://%s ，请添加 renew-https.sh 每日定时执行。\n' "$DOMAIN"
