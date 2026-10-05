# V1.0.1 远程安装与更新验收

日期：2026-10-05。新增根目录 `install.sh`，满足首次安装与 GitHub 推送新版后使用同一条命令更新：

```bash
curl -fsSL https://raw.githubusercontent.com/xiaowork-dev/Automated-Delivery/main/install.sh | sudo bash
```

默认目录 `/opt/Automated-Delivery`，跟踪本仓库 `main`。自动准备缺失的 Git / Docker / Compose；已有 Docker Engine 不重复安装。更新通过 fast-forward 拉取，再调用已有部署脚本执行镜像构建、数据库备份和健康等待。配置、证书、备份与数据库卷保留，HTTPS 自动延续。部署成功后原子写入 `.deployment-state`。

## 本地验证

- `install.sh` 与测试脚本 Bash 语法通过；工作流 11 个 Bash 代码块语法通过。
- `scripts/test-install.sh` 实际运行 16 项全部通过：首装、重复运行、HTTPS、真实 Git fast-forward、环境/证书/备份保留、本地改动拒绝、分叉拒绝、来源校验、失败标记保护、健康拒绝、重试恢复、并发锁、忽略配置与远端 tracked 文件碰撞、残缺证书拒绝降级。
- 上述回归使用真实本地 bare Git，模拟 Docker 与部署器，阻断联网与包管理；在 Git Bash 验证实际 mkdir 锁路径。临时测试工作区经路径检查后清理，未读取真实环境配置。
- V1.0.1 前端生产构建通过；后端 `mvn verify` 构建通过，9 项服务/Redis 测试通过，4 项 MySQL 测试因本轮未配置独立测试库 URL 明确跳过。Linux CI 将执行真实 MySQL 全部 13 项。

## Linux CI

工作流已加入隔离安装回归，并在独立 Linux 运行环境下载已提交的 raw 安装脚本，实际经 `sudo -E bash` 安装到临时目录。Git 安装源使用本地 bare 快照锁定到 workflow commit，避免 main 在验收期间变化；镜像构建、数据库、Nginx 和浏览器均实际运行。

待该版本 CI 完成后在此补充：远程首装、重复安装、HTTPS 后自动更新、真实 HTTP/浏览器、Redis 降级和备份恢复的结果。依赖安装代码使用 Docker 官方签名 apt/RPM 仓库；CI 的 Docker 已准备好，尚未在每种空白发行版上实际安装 Engine。

目标公网服务器与真实域名仍未提供；公网访问、可信证书签发及目标服务器重启恢复沿用 V1.0.0 的待验收项。
