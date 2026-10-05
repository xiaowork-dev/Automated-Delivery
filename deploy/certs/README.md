# HTTPS 证书

生产域名的有效证书放在本目录：`fullchain.pem` 和 `privkey.pem`，再执行部署脚本的 HTTPS 模式。私钥及证书不提交 Git。证书须覆盖访问域名；续期后执行 `docker compose -f docker-compose.yml -f deploy/docker-compose.https.yml restart frontend`。

证书签发和续期须由服务器现有 ACME / 证书管理工具负责。这里不内置账号或自动购买域名。不要把自签证书作为公网验收依据。
