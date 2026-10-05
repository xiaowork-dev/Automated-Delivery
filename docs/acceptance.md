# V1.0.0 验收记录

日期：2026-10-05（Asia/Shanghai）。按开发文档完成模拟交易功能，未接入真实资金。

## 已完成的本地验收

| 项目 | 实际结果 |
| --- | --- |
| 后端服务、权限及 Redis 单元测试 | 9 项通过 |
| 真实 MySQL 8.0.45 服务集成测试 | 4 项通过：20 轮单库存五订单竞争、20 单竞争 10 条库存、同单 20 并发重试、数据库故障完整回滚 |
| 真实 MySQL HTTP 验收 | 42 项通过、139 次请求；含注册、权限、快照、导入、支付、兑换、作废、禁用账号、密码变更和 50 并发商品请求 |
| 浏览器交易与后台 | Edge 实际运行 16 项通过；桌面与 390px 手机布局检查通过；无 JavaScript 运行错误 |
| 超时与过期任务 | 实际修改独立测试记录有效期，订单转为 EXPIRED、过期支付拒绝、卡密转为 EXPIRED、可用库存排除过期卡密 |
| 统一错误响应 | 已实际验证 401、403、404、405、409，以及业务错误码 |
| 前端生产构建 | Vite 6.4.3 构建通过；npm audit 无漏洞 |
| 部署脚本 | Bash、PowerShell、Python 语法与 YAML 解析通过；版本同步在隔离副本验证通过 |

HTTP 本次测量中位数为 32.55ms，P95 为 101.14ms，统计范围为本机本轮 139 次请求。原始报告保存在本地 `artifacts/api-acceptance.json`、`artifacts/browser/report.json`，不提交临时测试账号、兑换码和截图。

## 修复与确认

真实 MySQL 首轮测试发现 REPEATABLE_READ 下库存索引间隙锁竞争导致部分请求返回 RESOURCE_BUSY。支付事务改为 READ_COMMITTED，保留订单行锁、卡密行锁、状态条件更新和唯一约束。修复后真实数据库竞争稳定返回一单成功、其他明确 OUT_OF_STOCK。

兑换码通过前端内存草稿传递，不进入 URL，避免出现在浏览器历史、Referer 和 Nginx 访问日志。修改密码后旧 JWT 失效，禁用用户后旧令牌被拒绝。数据库触发器故障测试证明卡密、订单支付时间、状态和发货日志一起回滚，移除故障后原订单可以重试成功。

## Docker 与上线验收

本机没有 Docker，容器启动、Nginx 路由、TLS 配置、Redis 故障降级和备份恢复交由 GitHub Actions 的独立 Linux 环境执行。CI 运行结果将在完成后补充到此文件。[Actions](https://github.com/xiaowork-dev/Automated-Delivery/actions)

公网 Linux 服务器、真实域名、可信证书签发与外网访问尚未提供验收环境，因此仍是未完成的上线验收项。已提供 `scripts/enable-https.sh`、`scripts/deploy.sh` 和对应 PowerShell 部署入口；取得服务器与域名后可以继续执行这部分。

## Git 与后续更新

设计、后端、前端和部署按阶段独立提交到用户指定仓库。`AGENTS.md` 保留每次版本更新后测试、提交并推送的用户要求；`scripts/release.sh` / `scripts/release.ps1` 同步版本号、测试、检查敏感文件、提交并原子推送分支和标签。
