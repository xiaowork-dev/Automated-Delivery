# 实施与验收计划

依据用户提供的《模拟交易自助发货网站详细开发文档》，采用 Java 21 + Spring Boot 3 + MyBatis-Plus + MySQL 8、Vue 3 + Vite + Element Plus。只实现模拟支付。Redis 提供商品缓存和限流，失败时数据库仍是库存与支付幂等的事实来源。

## 阶段

1. 工程、数据库迁移、统一 API、部署骨架。
2. 商品与卡密库存、认证和后台权限。
3. 订单快照、模拟支付事务与并发发货。
4. 自助兑换、兑换记录、状态机与审计。
5. 买家页面和后台、超时任务、缓存与限流。
6. 构建、API 集成与并发测试、浏览器流程检查、一键部署和版本提交。

每个阶段保留验收证据；用户已明确授权版本更新后提交并推送指定仓库。不得提交环境文件、数据库备份和令牌。

## API 契约

前缀 `/api/v1`。返回 `{code: 0, message: "success", data: ..., requestId: "..."}`；错误 code 为字符串，HTTP 状态按文档。分页为 `{records, total, page, size}`。字段使用 camelCase，金额 JSON 数字，时间 ISO 8601。认证响应 `{token, user: {id, username, role}}`。角色为 `USER` / `ADMIN`。

- `POST /auth/register`、`POST /auth/login`：`{username,password}`，响应认证信息。
- `GET /auth/me`：用户信息；`PUT /auth/password`：`{currentPassword,newPassword}`。
- `GET /products?page&size&keyword`、`GET /products/{id}`：商品 `{id,name,subtitle,price,coverUrl,description,status,stock,stockLabel}`。
- `POST /orders`：`{productId}`。订单 `{id,orderNo,userId,productId,productName,unitPrice,amount,status,createdAt,expireAt,paidAt,deliveredAt,redeemCode}`。`redeemCode` 仅在已发货/已完成时公开给所有者或管理员，字段 `{id,code,status,expiredAt,usedAt}`。
- `GET /orders?page&size&status`、`GET /orders/{orderNo}`。
- `POST /orders/{orderNo}/mock-pay`、`POST /orders/{orderNo}/cancel`：返回订单。
- `POST /redeem`：`{code}`，响应 `{orderNo,productName,status,usedAt,message}`。
- `GET /redeem/records?page&size`：兑换记录。
- `GET /admin/dashboard`：`{productCount,todayOrders,availableCodes,deliveredOrders,lowStockProducts}`。
- `GET /admin/products?page&size&keyword&status`；`POST /admin/products`、`PUT /admin/products/{id}`：商品字段（新增 status 可选），返回商品。
- `POST /admin/redeem-codes/import`：`{productId,codes,expiredAt}`，codes 为多行文本，响应 `{successCount,duplicateCount,invalidCount}`。
- `GET /admin/redeem-codes?page&size&productId&status`：卡密记录（默认脱敏 code）及绑定订单。
- `POST /admin/redeem-codes/{id}/disable`：`{reason}`，仅未使用卡密；审计操作。
- `GET /admin/orders?page&size&keyword&status`、`GET /admin/orders/{orderNo}`、`POST /admin/orders/{orderNo}/close`：关闭仅 WAIT_PAY；审计。
- `GET /admin/users?page&size&keyword`；`PUT /admin/users/{id}/status`：`{status: 0|1}`，审计。
- `GET /admin/logs?page&size`：操作日志。
- `/actuator/health`：部署健康检查。

## 关键一致性设计

支付事务先锁订单、校验所有权与状态，再 `FOR UPDATE` 锁可用卡密并带状态条件更新，最后更新订单及交付日志。订单与卡密一对一唯一约束。重复支付返回已有卡密。任何失败整体回滚。兑换锁卡密并检查所有权/有效期，原子更新为 USED、写唯一成功记录、订单变为 COMPLETED。

## 最终验收

构建通过，完整 API 交易流程通过，用户越权被拒绝，卡密去重导入通过，价格快照通过，重复支付十次仍一单一卡，单库存五订单并发只有一单发货，重复兑换拒绝，事务失败全部回滚。前端桌面和移动端核心流程可用。部署脚本、持久化、HTTPS 配置及备份恢复检查完成。公网部署需真实服务器与域名，无法凭空验证。
