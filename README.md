# Automated Delivery · 模拟交易自助发货网站

依据《模拟交易自助发货网站详细开发文档》实现的数字商品交易与兑换系统。使用 **Java 21 / Spring Boot 3 / MyBatis-Plus / MySQL 8 / Redis / Vue 3 / Vite / Element Plus**，全程仅模拟支付。

仓库：[xiaowork-dev/Automated-Delivery](https://github.com/xiaowork-dev/Automated-Delivery)。实施顺序、接口契约见 [docs/implementation-plan.md](docs/implementation-plan.md)。最终运行验收以 [docs/acceptance.md](docs/acceptance.md) 的实际结果为准；提供配置与脚本不代表已经完成公网部署。

## 已实现的业务

- 游客浏览商品；买家注册、登录、个人资料与密码修改。
- 单商品单数量订单、历史价格快照、30 分钟待支付超时、取消订单。
- 模拟支付事务发货：先锁订单，再锁有效库存卡密；重复支付返回同一卡密。
- 订单归属校验、JWT 认证、后台角色权限；一张卡密最多绑定一个订单。
- 自助兑换、有效期与用户校验、唯一成功兑换记录、订单完成。
- 后台商品上下架、文本 / CSV 导入、卡密状态查询与作废、订单关闭、用户状态与审计。
- Redis 商品缓存和限流；Redis 不可用时库存与支付一致性仍由 MySQL 保证。
- Docker Compose、Nginx history 路由与 API 代理、HTTPS、健康检查、升级前备份和恢复。

## 一键启动

安装 Docker Engine + Compose v2（Linux），或 Docker Desktop（Windows，Linux 容器模式）。在项目根目录执行：

```bash
# Linux / Git Bash
bash scripts/deploy.sh
```

```powershell
# Windows PowerShell 5.1+ / PowerShell 7
powershell -ExecutionPolicy Bypass -File scripts/deploy.ps1
```

脚本构建前后端、首次生成私密 `.env`、启动 MySQL / Redis / 后端 / Nginx，并等待健康检查。默认入口为 `http://localhost`。首次管理员用户名为 `admin`，随机初始密码保存在服务器本地 `.env` 的 `ADMIN_PASSWORD`；脚本不会输出密码。请自行在本地查看，首次登录后到个人中心修改密码。已经存在的管理员不会在重启时被环境配置重置密码。

数据库、应用日志分别保存在 Compose 命名卷 `mysql-data` 与 `app-logs`。MySQL、Redis 和后端不发布主机端口。生产默认不导入演示商品；管理员创建商品、导入合法测试卡密后即可完成买家流程。若仅作独立演示，可在首次启动前本地设置 `APP_SEED_DEMO=true`。

升级现有实例时，脚本先构建镜像、备份当前数据库，再更新容器；备份失败则中止升级。脚本不执行 `down -v`，不会删除数据库卷。Flyway 在应用启动时维护数据库迁移；应用升级前保留 Git 版本与数据库备份。恢复旧数据库时，应同时使用匹配的应用版本。

## Linux 公网 HTTPS

服务器准备好 Docker / Compose、域名 A/AAAA 解析与入站 80 / 443 后，在项目根目录执行：

```bash
bash scripts/enable-https.sh shop.example.com admin@example.com
```

它先启动 HTTP 服务供 ACME 校验，调用官方 Certbot 镜像申请证书，再开启 HTTPS。签发失败会中止并保留可诊断的 HTTP 部署。必须使用自己控制并已解析到服务器的真实域名，脚本不会购买服务器或域名。生产使用标准 80 / 443 端口。

已经拥有有效证书时，将证书链和私钥分别放到 `deploy/certs/fullchain.pem`、`deploy/certs/privkey.pem`，执行：

```bash
bash scripts/deploy.sh --https
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts/deploy.ps1 -Https
```

HTTPS 模式 HTTP 自动 301 跳转，`/api/` 只经 HTTPS 对外服务。`/healthz` 和 ACME 校验路径保留 HTTP 可用。手工提供的证书由原证书管理工具续期，之后重新加载 Nginx。Certbot 模式可在服务器 crontab 添加每日续期检查与数据库备份（将 `/opt/Automated-Delivery` 替换为实际目录）：

```cron
15 3 * * * /usr/bin/bash /opt/Automated-Delivery/scripts/backup.sh >> /var/log/automated-delivery-backup.log 2>&1
30 3 * * * /usr/bin/bash /opt/Automated-Delivery/scripts/renew-https.sh >> /var/log/automated-delivery-certificate.log 2>&1
```

`renew-https.sh` 只在证书内容变更后校验并加载 Nginx，不重建业务服务。证书私钥、ACME 账号、`.env`、数据库备份都不进入 Git。当前环境未提供真实服务器 / 域名时，公网访问、可信证书签发和服务器重启后的恢复需在该服务器补充验收。

## 配置

`.env.example` 仅说明变量，包含占位值，不能直接作为生产配置。部署脚本可自动生成随机配置；不要将真实 `.env` 提交、截图或粘贴到聊天中。

| 变量 | 用途 |
| --- | --- |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | MySQL 业务库、业务账号与密码 |
| `DB_ROOT_PASSWORD` | 数据库首次初始化的管理员密码 |
| `JWT_SECRET` | 至少 32 字符的随机签名密钥 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 首次创建管理员，密码至少 8 字符 |
| `APP_SEED_DEMO` | 首次生成演示商品，生产默认 `false` |
| `REDIS_ENABLED` | 商品缓存与限流，默认 `true` |
| `HTTP_PORT` / `HTTPS_PORT` | 默认 `80` / `443`；公网 HTTPS 保持标准端口 |
| `APP_BASE_URL` | 运维记录的访问地址；前后端请求使用同源 API |
| `BACKUP_RETENTION_DAYS` | 备份保留天数，至少 7 天 |

后端单独启动时，还支持 `DB_HOST`、`DB_PORT`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`SERVER_PORT`、`LOG_PATH`、`ORDER_EXPIRY_MINUTES` 和 `SCHEDULER_ENABLED`。Compose 自动设置内部主机名与端口，无须暴露数据库到公网。

## 不使用 Docker 的本地开发

需要 Java 21、Maven 3.9+、Node.js 22 / npm、独立 MySQL 8 库。由数据库管理员创建业务库并给业务账号该库的权限，然后在当前终端设置 `DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD / JWT_SECRET / ADMIN_USERNAME / ADMIN_PASSWORD`。不要在 README、源码或启动命令中写入真实密钥。

```bash
# 终端 1：准备环境变量后启动 API（默认 8080）
cd backend
mvn spring-boot:run

# 终端 2：开发页面（Vite 转发 /api 到 8080）
cd frontend
npm ci
npm run dev
```

Redis 可选，本地设置 `REDIS_ENABLED=false` 即可关闭。后端自动执行 `backend/src/main/resources/db/migration` 中的 Flyway 迁移，勿手工修改已在实例执行过的迁移文件。

## 验证

```bash
cd backend
mvn -B verify

cd ../frontend
npm ci
npm run build
```

后端测试采用 H2 的 MySQL 兼容模式验证事务、幂等、并发和回滚边界；真实 MySQL HTTP 验收独立执行，不能把 H2 结果当成 MySQL 行锁证明。针对独立测试部署设置 `TEST_BASE_URL / ADMIN_USERNAME / ADMIN_PASSWORD` 后，在根目录运行：

额外设置 `TEST_MYSQL_URL / TEST_MYSQL_USER / TEST_MYSQL_PASSWORD` 后，`mvn verify` 会启用真实 MySQL 并发回归：20 轮单库存五订单竞争、20 单竞争 10 条库存、同单 20 次重试及数据库触发器故障回滚。必须使用专门的空测试库；触发器故障测试需要该测试实例的相应权限，不能在生产库执行。没有配置此 URL 时，这 4 项测试明确跳过。

```bash
python scripts/api-acceptance.py
```

此脚本在数据库创建具有随机名称的测试用户、商品、订单和卡密，不清空现有数据。请使用专门测试库。结果写入 `artifacts/api-acceptance.json`，不输出密码、JWT 或完整卡密。

浏览器验收使用 Playwright。在后端、前端或 Compose 已启动、测试库有上架且有库存的商品时，设置 `TEST_WEB_URL / ADMIN_USERNAME / ADMIN_PASSWORD`，然后执行：

```bash
cd frontend
npm ci
npx playwright install --with-deps chromium
npm run test:e2e
```

`TEST_WEB_URL` 应指向实际页面入口，例如本地 Vite 的 `http://127.0.0.1:5173` 或 Compose 的 `http://127.0.0.1:8088`。Windows 可设置 `BROWSER_CHANNEL=msedge` 使用已安装的 Edge；不设置时使用 Playwright Chromium。脚本验证买家与管理员流程和移动端页面，结果与截图写入 `artifacts/browser/`。它会创建测试记录，仍应使用专门测试库。

GitHub Actions 将运行后端测试、真实 MySQL HTTP 验收、前端构建、Docker Compose 启动、Nginx 路由、浏览器交易流程、备份恢复及 TLS 配置检查。TLS 检查使用仅 CI 的自签证书，公网可信证书的签发必须另外在目标服务器验证。CI 的结果以仓库 Actions 实际状态为准。

## 备份与恢复

```bash
bash scripts/backup.sh
bash scripts/restore.sh backups/database-YYYYMMDDTHHMMSSZ-xxx.sql.gz --confirm
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts/backup.ps1
powershell -ExecutionPolicy Bypass -File scripts/restore.ps1 -Backup backups/database-xxx.sql.gz -Confirm
```

备份使用事务一致性的 `mysqldump --single-transaction`，保存在根目录 `backups/`，压缩并生成 SHA256。至少保留 7 天；可将备份复制到安全的异机存储。备份包含用户资料和卡密，应限制访问。恢复先检查文件与摘要并备份当前库，然后停止业务服务、导入 SQL、启动原容器并检查健康。恢复会覆盖同名表，因此必须显式提供恢复参数；导入失败时服务保持停止以供排查。

## 每次版本更新提交 Git

用户已授权每次版本更新后提交并推送该仓库。推荐每个可运行功能独立提交；正式版本使用发布脚本，自动同步 `VERSION`、Maven、前端与 lockfile 的版本，测试与构建通过后列出文件、检查敏感文件、提交、打标签并原子推送当前分支与标签：

```bash
bash scripts/release.sh 1.0.1 "fix(payment): 修复模拟支付边界"
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts/release.ps1 -Version 1.0.1 -Message "fix(payment): 修复模拟支付边界"
```

需要本机 Git 已登录并对 `origin` 有写权限，以及 Java / Maven / Node / Python。提供 `TEST_BASE_URL` 时还会执行真实部署的 API 验收；正式发布前应在独立测试库完成这项验收。测试失败不会提交或推送，已修改的版本元数据会保留供修复。推送失败时本地提交和标签保留，应排查认证 / 网络后重试 `git push --atomic origin HEAD v1.0.1`；不要重复执行同版本发布脚本。

版本提交与部署分开：先确认 CI 验收，再在服务器 `git pull --ff-only` 后运行 `bash scripts/deploy.sh --https`。已有 HTTPS 站点升级要继续带 `--https`，以使用证书配置。

## 日常排查

```bash
docker compose ps
docker compose logs --tail 100 backend frontend
docker compose exec backend curl -fsS http://localhost:8080/actuator/health
```

业务日志采用 requestId 和实体 ID 追踪，不应记录完整卡密、密码或令牌。Nginx / 容器日志限制单文件大小与保留份数。禁止接入真实收付款；真实支付属于独立需求和迭代。
