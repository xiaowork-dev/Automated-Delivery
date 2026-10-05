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

工作流已加入隔离安装回归，并在独立 Linux 运行环境下载已提交的 raw 安装脚本，实际经 `sudo bash`（仅保留本次测试所需配置变量）安装到临时目录。Git 安装源使用本地 bare 快照锁定到 workflow commit，避免 main 在验收期间变化；镜像构建、数据库、Nginx 和浏览器均实际运行。

首轮 CI 的远程首次安装和全部容器健康检查通过；重复运行测试因 sudo 保留 runner HOME 导致 Docker 缓存锁文件归 root 所有而退出。CI 已改为独立 root Docker 缓存目录及明确环境变量列表，应用安装脚本未变。

[最终 CI 37322815204](https://github.com/xiaowork-dev/Automated-Delivery/actions/runs/37322815204) 对提交 `4c52e91d16e03b842f45e18a253a0af594bb8813` 验收成功，四个任务 installer / backend / frontend / deployment 全部通过。后续仅补充本验收文档，不改变已验证的安装、应用和工作流代码。

| 项目 | 实际结果 |
| --- | --- |
| 隔离安装回归 | 16 项通过；真实 Git 快进与冲突保护；Linux 实际 flock 锁 |
| 后端与前端 | 真实 MySQL 全部 13 项后端测试通过；API 验收及前端生产构建通过 |
| 远程一条命令首装 | 实际下载已提交的 raw install.sh，经 sudo 安装；四个容器健康，成功记录 V1.0.1 / HTTPS=false |
| 再次执行更新入口 | 同源 Git 更新、重建、备份及健康检查通过；成功状态记录更新 |
| HTTP 交易 | 42 项 / 139 次请求通过；中位数 26.27ms、P95 156.38ms |
| 浏览器 | 16 项买家、后台、移动端验收通过，无 JavaScript 运行错误 |
| HTTPS 自动延续 | 创建 CI 自签证书并开启 TLS 后，不传 --https 再次运行安装器，成功记录 HTTPS=true；HTTPS 页面和 HTTP 301 验证通过 |
| Redis 停机降级 | HTTPS 下 42 项 / 139 次请求通过；P95 598.97ms |
| 备份与恢复 | gzip / SHA256 校验、停机恢复、健康启动通过；HTTPS 下再次完成 42 项 / 139 次请求，P95 217.22ms |

主分支 raw 安装入口已实际下载得到 HTTP 200，并验证 Git blob 与提交一致。CI 上传了测试报告和浏览器截图，没有上传环境文件、私钥或数据库备份。

依赖安装代码使用 Docker 官方签名 apt/RPM 仓库；CI 的 Docker 已准备好，尚未在每种空白发行版上实际安装 Engine。

目标公网服务器与真实域名仍未提供；公网访问、可信证书签发及目标服务器重启恢复沿用 V1.0.0 的待验收项。
