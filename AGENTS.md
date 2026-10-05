# 项目协作约定

本项目根据用户提供的数字商品模拟交易自助发货开发文档实现。技术栈为 Java 21 / Spring Boot 3 / MyBatis-Plus / MySQL 8 / Vue 3 / Vite / Element Plus。保持前后端分离的单体架构；支付仅为模拟，不接入真实资金。

## 用户的长期要求

- 每次版本更新后，完成相应构建、测试和变更说明，执行 Git commit 并 push 到 `https://github.com/xiaowork-dev/Automated-Delivery`。用户已经授权此流程，无需每次重复询问。
- 一次提交聚焦一项已完成的功能或修复。不能 force push，也不能重写已有历史。
- 维护一键部署脚本与说明，确保升级前备份、数据卷持久化、前端路由刷新正常、生产 HTTPS 配置可用。
- 未实际验证的事项必须明确记录，不能把静态审查视为真实部署或验收通过。

## 实现与验证

- Controller 只负责参数和响应，事务与状态判断在 Service，SQL 在 Mapper。
- 订单与卡密状态使用枚举。金额使用 BigDecimal 与 DECIMAL。
- 库存来自未分配且未过期的兑换码；Redis 只作辅助。
- 支付必须满足行锁、事务、一单一卡及重复请求幂等；兑换必须验证所有权、状态、有效期并只成功一次。
- 保留核心并发、回滚、权限测试。修改相关业务时运行相应测试，再提交。
- 数据库结构变化通过新的 Flyway migration 完成；不得修改已上线的迁移校验和。
- 不读取、提交或输出现有环境文件、服务器密码、私钥、JWT 和备份数据。仅提交无有效秘密的配置示例。
- 参阅 `docs/implementation-plan.md`、`docs/architecture.md` 和 README 的运行/验收方式。
