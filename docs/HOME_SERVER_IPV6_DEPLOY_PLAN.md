# Noto 闲置笔记本 IPv6 部署计划

> 适用场景：闲置笔记本已安装 Ubuntu Server，已开启 IPv6，已能 SSH 登录，Docker 已安装，域名正在申请中。
>
> 目标：把 Noto 部署为一台家庭/个人生产服务器，通过域名 HTTPS 访问；数据库和 MinIO 不暴露公网；支持后续更新、备份和故障恢复。

---

## 1. 总体方案

推荐采用“宿主机 Nginx + Docker Compose 应用栈”的部署方式：

```text
公网 IPv6 / 域名 :443
    ↓
Ubuntu Server Nginx（HTTPS、证书、反向代理）
    ↓ 127.0.0.1:8080
noto-frontend 容器
    ↓ Docker 内网
noto-backend / noto-db / noto-minio
```

关键原则：

- 只对公网开放 `22`、`80`、`443`。
- `PostgreSQL`、`MinIO`、后端端口不直接暴露公网。
- Noto 应用容器只监听本机回环地址，由宿主机 Nginx 对外服务。
- 域名未生效前，可先在服务器本机用 `curl http://127.0.0.1:8080/api/v1/health` 验证应用栈。
- 域名生效后，再申请 HTTPS 证书并对外开放。

---

## 2. 前置检查

### 2.1 在服务器上确认系统和 Docker

```bash
uname -a
lsb_release -a
docker --version
docker compose version
```

建议：

- Ubuntu Server 22.04 或 24.04。
- Docker Compose v2.24+。项目的生产 override 使用了 `ports: !reset []`，旧版 Compose 可能不支持。

如果 Compose 太旧：

```bash
sudo apt update
sudo apt install -y docker-compose-plugin
docker compose version
```

### 2.2 确认 IPv6 可用

```bash
ip -6 addr
ip -6 route
curl -6 https://ifconfig.co
```

期望：

- 网卡上有公网 IPv6 地址，不是只以 `fe80::` 开头的链路本地地址。
- `curl -6 https://ifconfig.co` 能返回公网 IPv6。

### 2.3 确认外部能访问服务器 IPv6

在另一台网络环境中测试：

```bash
ping -6 你的IPv6地址
ssh 用户名@[你的IPv6地址]
```

如果 ping 不通或 SSH 不通，优先检查：

- 光猫/路由器是否给服务器分配公网 IPv6。
- 路由器 IPv6 防火墙是否放行入站。
- Ubuntu 防火墙是否放行 SSH。
- 运营商是否限制入站 IPv6。

---

## 3. 服务器基础加固

### 3.1 创建部署目录

推荐部署在 `/opt/noto`：

```bash
sudo mkdir -p /opt/noto
sudo chown -R $USER:$USER /opt/noto
```

### 3.2 配置防火墙

如果使用 UFW：

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

不要开放这些端口到公网：

- `5432`：PostgreSQL
- `9000` / `9001`：MinIO API / 控制台
- `9086`：后端 API
- `8080`：容器前端入口，后续只给本机 Nginx 访问

### 3.3 SSH 建议

建议使用密钥登录，关闭密码登录：

```bash
sudo nano /etc/ssh/sshd_config
```

建议项：

```text
PasswordAuthentication no
PermitRootLogin no
```

重启 SSH：

```bash
sudo systemctl restart ssh
```

注意：关闭密码登录前，先确认密钥登录已经可用，避免把自己锁在门外。

---

## 4. 获取项目代码

### 4.1 从 Git 仓库克隆

如果代码已推送到 GitHub/Gitee：

```bash
git clone <你的仓库地址> /opt/noto
cd /opt/noto
```

### 4.2 如果暂时没有远程仓库

可先用 `rsync` 从本机同步：

```bash
rsync -av --delete \
  --exclude .git \
  --exclude node_modules \
  --exclude target \
  --exclude dist \
  ./ 用户名@[服务器IPv6地址]:/opt/noto/
```

长期建议仍使用 Git 管理发布，后续更新会简单很多。

---

## 5. 配置生产环境变量

在服务器项目根目录：

```bash
cd /opt/noto
cp deploy/env.prod.example .env
nano .env
```

必须修改：

```env
POSTGRES_DB=noto_zhihui
POSTGRES_USER=postgres
POSTGRES_PASSWORD=改成强密码

NOTO_JWT_SECRET=至少32位随机字符串
NOTO_DEMO_ENABLED=false

NOTO_MINIO_ENABLED=true
MINIO_ROOT_USER=noto_minio
MINIO_ROOT_PASSWORD=改成强密码

BACKEND_PORT=9086
FRONTEND_PORT=8080
```

如果暂时不启用 AI：

```env
NOTO_AI_ENABLED=false
AI_DASHSCOPE_API_KEY=
```

如果启用通义/百炼 AI：

```env
NOTO_AI_ENABLED=true
AI_DASHSCOPE_API_KEY=你的Key
```

生成随机密钥示例：

```bash
openssl rand -base64 48
```

上线前检查：

```bash
chmod +x deploy/*.sh
./deploy/prod-check.sh --strict
```

---

## 6. 域名未生效前：先启动内网生产栈

域名申请中也可以先启动容器，验证应用本身没问题：

```bash
cd /opt/noto
./deploy/prod-up.sh
```

该脚本等价于：

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml up -d --build
```

检查容器：

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml ps
docker ps --filter name=noto-
```

检查健康状态：

```bash
curl -s http://127.0.0.1:8080/api/v1/health
```

期望返回类似：

```json
{"code":0,"data":{"status":"UP"}}
```

查看日志：

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f frontend
```

---

## 7. 域名解析计划

### 7.1 推荐记录

假设域名是：

```text
noto.example.com
```

由于你当前重点是 IPv6，DNS 至少配置：

| 类型 | 主机记录 | 值 |
|------|----------|----|
| `AAAA` | `noto` | 服务器公网 IPv6 |

如果未来有公网 IPv4，也可以加：

| 类型 | 主机记录 | 值 |
|------|----------|----|
| `A` | `noto` | 服务器公网 IPv4 |

### 7.2 家宽 IPv6 变动问题

很多家庭宽带 IPv6 前缀会变化。需要先判断你的 IPv6 是否稳定：

```bash
curl -6 https://ifconfig.co
```

连续几天或重拨路由后对比结果。

如果 IPv6 会变化，建议：

- 使用域名服务商提供的 DDNS。
- 或用 Cloudflare API / DNSPod API 写一个定时脚本更新 `AAAA` 记录。
- 或在路由器上配置 DDNS，如果路由器支持 IPv6 DDNS。

### 7.3 DNS 生效验证

在任意机器上：

```bash
dig AAAA noto.example.com
ping -6 noto.example.com
```

如果没有 `dig`：

```bash
nslookup -type=AAAA noto.example.com
```

---

## 8. 安装并配置宿主机 Nginx

### 8.1 安装 Nginx 和 Certbot

```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
```

启动并设置开机自启：

```bash
sudo systemctl enable --now nginx
sudo systemctl status nginx
```

### 8.2 域名证书申请前的临时 HTTP 配置

域名刚生效时，先创建一个 HTTP 配置，方便 Certbot 验证：

```bash
sudo nano /etc/nginx/sites-available/noto
```

内容：

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name noto.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

启用站点：

```bash
sudo ln -sf /etc/nginx/sites-available/noto /etc/nginx/sites-enabled/noto
sudo nginx -t
sudo systemctl reload nginx
```

验证：

```bash
curl -I http://noto.example.com
curl -s http://noto.example.com/api/v1/health
```

---

## 9. 申请 HTTPS 证书

域名 `AAAA` 记录生效，并且公网能访问 80 端口后：

```bash
sudo certbot --nginx -d noto.example.com
```

按提示输入邮箱、同意协议，并选择自动重定向 HTTP 到 HTTPS。

验证自动续期：

```bash
sudo certbot renew --dry-run
```

---

## 10. 替换为项目正式 Nginx 配置

项目已有配置文件：

```text
deploy/nginx/noto.conf
```

它包含：

- IPv4/IPv6 监听。
- HTTPS。
- `/api/v1/ai/` SSE 流式问答关闭缓冲。
- 上传大小 `100m`。
- 基础安全头。

替换域名后写入。先把变量改成你的真实域名：

```bash
cd /opt/noto
DOMAIN=你的真实域名
sudo sed "s/noto.example.com/${DOMAIN}/g" deploy/nginx/noto.conf \
  | sudo tee /etc/nginx/sites-available/noto
```

检查并重载：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

最终验证：

```bash
curl -I https://你的真实域名
curl -s https://你的真实域名/api/v1/health
```

---

## 11. 首次上线验收清单

### 11.1 访问与登录

- [ ] `https://你的域名` 可以打开。
- [ ] 可以注册或登录。
- [ ] 不再使用默认演示账号作为公开账号。
- [ ] `https://你的域名/api/v1/health` 返回 `code: 0`。

### 11.2 核心功能

- [ ] 新建笔记。
- [ ] 编辑笔记。
- [ ] 新建待办。
- [ ] 待办状态切换。
- [ ] 上传附件或图片。
- [ ] 分享链接能正常打开。

### 11.3 AI 功能

如果 `NOTO_AI_ENABLED=true`：

- [ ] `AI 助手`能返回内容。
- [ ] AI 流式回复不会中途断开。
- [ ] RAG/知识库问答能引用文档。

如果 AI 暂未启用：

- [ ] 页面能正常提示 AI 未启用，不影响笔记和待办主流程。

### 11.4 安全暴露

在服务器上：

```bash
ss -lntp
```

期望：

- `80`、`443` 对公网监听。
- `8080` 只监听 `127.0.0.1`。
- `9086` 只监听 `127.0.0.1`。
- `5432`、`9000`、`9001` 不对公网监听。

---

## 12. 日常运维

### 12.1 查看服务状态

```bash
cd /opt/noto
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml ps
docker ps --filter name=noto-
```

### 12.2 查看日志

```bash
cd /opt/noto
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f frontend
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f db
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f minio
```

### 12.3 重启

```bash
cd /opt/noto
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml restart
```

仅重启后端：

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml restart backend
```

### 12.4 更新应用

如果使用 Git：

```bash
cd /opt/noto
./deploy/prod-backup.sh /var/backups/noto
git pull
./deploy/prod-up.sh
```

如果用 rsync 同步代码：

```bash
cd /opt/noto
./deploy/prod-backup.sh /var/backups/noto
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml up -d --build
```

---

## 13. 备份策略

Noto 需要备份两类数据：

1. PostgreSQL：笔记、待办、用户、配置等结构化数据。
2. MinIO：附件、图片等对象文件。

### 13.1 PostgreSQL 备份

手动备份：

```bash
cd /opt/noto
sudo mkdir -p /var/backups/noto
sudo chown -R $USER:$USER /var/backups/noto
./deploy/prod-backup.sh /var/backups/noto
```

设置每日定时备份：

```bash
crontab -e
```

加入：

```cron
20 3 * * * cd /opt/noto && ./deploy/prod-backup.sh /var/backups/noto >/var/log/noto-backup.log 2>&1
```

保留最近 14 天：

```cron
50 3 * * * find /var/backups/noto -name 'noto-*.sql.gz' -mtime +14 -delete
```

### 13.2 MinIO 数据备份

项目使用 Docker 命名卷：

```text
noto_minio_data
```

最简单的冷备份方式：

```bash
docker run --rm \
  -v noto_minio_data:/data:ro \
  -v /var/backups/noto:/backup \
  alpine \
  tar czf /backup/noto-minio-$(date +%Y%m%d-%H%M%S).tar.gz -C /data .
```

也可以后续使用 MinIO Client `mc mirror` 同步到另一块硬盘、NAS 或对象存储。

### 13.3 备份落盘建议

闲置笔记本单机部署最怕硬盘损坏。建议至少满足一个：

- 每日备份到另一块移动硬盘。
- 每日备份到 NAS。
- 每日备份到云盘/对象存储。
- 每周手动下载一次 `/var/backups/noto`。

---

## 14. 恢复流程预案

### 14.1 恢复 PostgreSQL

停止后端，避免写入：

```bash
cd /opt/noto
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml stop backend
```

恢复 SQL：

```bash
gunzip -c /var/backups/noto/noto-YYYYMMDD-HHMMSS.sql.gz \
  | docker exec -i noto-db psql -U postgres -d noto_zhihui
```

重启后端：

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml start backend
```

### 14.2 恢复 MinIO

先停止服务：

```bash
cd /opt/noto
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml down
```

恢复卷数据需要谨慎，建议先备份现有卷，再覆盖。示例：

```bash
docker run --rm \
  -v noto_minio_data:/data \
  -v /var/backups/noto:/backup \
  alpine \
  sh -c "cd /data && tar xzf /backup/noto-minio-YYYYMMDD-HHMMSS.tar.gz"
```

启动：

```bash
./deploy/prod-up.sh
```

---

## 15. 域名未备案或无法直连时的替代方案

如果你的网络环境导致公网 IPv6 入站不稳定，或域名/证书暂时无法使用，可以按优先级考虑：

### 15.1 Tailscale / ZeroTier 私有访问

适合个人使用，不公开到公网。

优点：

- 不依赖公网入站端口。
- 安全简单。
- 手机、电脑加入同一虚拟网络即可访问。

缺点：

- 访问者必须安装客户端。
- 不适合公开分享。

### 15.2 Cloudflare Tunnel

适合没有公网 IPv4，IPv6 入站受限，但希望域名 HTTPS 访问。

优点：

- 不需要开放公网入站。
- 自动 HTTPS。

缺点：

- 依赖 Cloudflare。
- 大文件上传、长连接、国内访问体验可能受网络影响。

### 15.3 Caddy 替代 Nginx

如果想减少证书配置复杂度，可以用 Caddy：

```caddy
noto.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

Caddy 会自动申请和续期证书。后续需要再确认 SSE 和上传大小配置。

---

## 16. 推荐执行顺序

### 阶段 A：现在就能做

- [ ] 确认服务器 IPv6 是公网地址。
- [ ] 配置 UFW，只开放 SSH、HTTP、HTTPS。
- [ ] 克隆或同步项目到 `/opt/noto`。
- [ ] 配置 `.env` 强密码和 JWT。
- [ ] 运行 `./deploy/prod-check.sh --strict`。
- [ ] 运行 `./deploy/prod-up.sh`。
- [ ] 用 `curl http://127.0.0.1:8080/api/v1/health` 验证应用。
- [ ] 配置数据库备份目录。

### 阶段 B：域名申请成功后

- [ ] 添加 `AAAA` 记录到服务器公网 IPv6。
- [ ] 等 DNS 生效，验证 `dig AAAA 你的域名`。
- [ ] 安装 Nginx 和 Certbot。
- [ ] 配置 HTTP 反向代理。
- [ ] 申请 HTTPS 证书。
- [ ] 替换为 `deploy/nginx/noto.conf` 正式配置。
- [ ] 验证 `https://你的域名/api/v1/health`。

### 阶段 C：稳定运行

- [ ] 设置 PostgreSQL 每日备份。
- [ ] 设置 MinIO 定期备份。
- [ ] 记录恢复流程并演练一次。
- [ ] 如果 IPv6 会变，配置 DDNS。
- [ ] 定期更新系统和 Docker 镜像。

---

## 17. 常见问题

### Q1：域名还没下来，能不能先用 IPv6 地址访问？

可以用于临时测试 HTTP，但不建议作为正式入口。HTTPS 证书通常需要域名，不会给裸 IPv6 地址签发普通证书。

本机验证用：

```bash
curl http://127.0.0.1:8080/api/v1/health
```

公网临时测试可先让 Nginx 监听 80，再访问：

```text
http://[你的IPv6地址]
```

### Q2：为什么不直接把 Docker 的 8080 暴露到公网？

生产环境需要 HTTPS、证书续期、上传大小、SSE、反向代理头和安全头。让宿主机 Nginx 统一处理会更稳，也能避免数据库和对象存储误暴露。

### Q3：MinIO 控制台怎么访问？

生产默认不开放 `9001`。需要临时访问时用 SSH 隧道：

```bash
ssh -L 9001:127.0.0.1:9001 用户名@服务器地址
```

但当前生产 compose 中 MinIO 未映射到宿主机。如果确实需要控制台，建议临时添加本机绑定端口，使用完再移除：

```yaml
minio:
  ports:
    - "127.0.0.1:9001:9001"
```

### Q4：家里断电怎么办？

建议：

- BIOS 设置来电自启。
- Ubuntu 设置 Docker 开机自启。
- Compose 服务已配置 `restart: unless-stopped`。
- 加一个小 UPS 会更稳。

### Q5：Docker 服务能否开机自动恢复？

确认 Docker 自启：

```bash
sudo systemctl enable docker
```

项目容器的 `restart: unless-stopped` 会在 Docker 启动后自动拉起。

---

## 18. 相关项目文件

| 文件 | 作用 |
|------|------|
| `deploy/env.prod.example` | 生产 `.env` 模板 |
| `deploy/docker-compose.prod.yml` | 生产 compose override，端口仅本机绑定 |
| `deploy/nginx/noto.conf` | 宿主机 Nginx HTTPS 配置 |
| `deploy/prod-check.sh` | 生产环境变量安全检查 |
| `deploy/prod-up.sh` | 生产启动脚本 |
| `deploy/prod-backup.sh` | PostgreSQL 备份脚本 |
| `docs/DEV_DEPLOY.md` | 项目完整开发与部署说明 |
| `deploy/README.md` | 云服务器生产部署速查 |
