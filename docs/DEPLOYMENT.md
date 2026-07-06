# Noto 部署指南

> 本文合并原开发部署指南、生产部署说明和闲置笔记本 IPv6 部署计划。

## 部署模式

| 模式 | 用途 | 入口 |
|------|------|------|
| 本地开发 | 写代码、调试、热重载 | [DEVELOPMENT.md](./DEVELOPMENT.md) |
| Docker 演示 | 一条命令跑全栈给别人看 | `.\scripts\demo-up.ps1` |
| 生产部署 | Linux + Docker Compose + Nginx HTTPS | `deploy/prod-up.sh` |
| 家庭 IPv6 服务器 | 闲置 Ubuntu Server 对外提供 HTTPS | 本文“IPv6 家庭服务器” |
| 飞牛 OS NAS | 飞牛 Docker + IPv6 域名；可直连高端口或反向代理 HTTPS | [FNOS_DOCKER_OPS.md](./FNOS_DOCKER_OPS.md) |

## Docker 演示

```powershell
copy .env.example .env
.\scripts\demo-up.ps1
```

访问 `http://localhost:8080`，默认演示账号 `demo` / `123456`。

停止：

```powershell
.\scripts\demo-down.ps1
```

## 生产总体架构

```text
Internet :443
    ↓
宿主机 Nginx（HTTPS、证书、SSE 反向代理）
    ↓ 127.0.0.1:8080
noto-frontend 容器
    ↓ Docker 内网
noto-backend / noto-db / noto-minio
```

原则：

- 只开放 `22`、`80`、`443`。
- PostgreSQL、MinIO、后端端口不直接暴露公网。
- `NOTO_DEMO_ENABLED=false`；如果需要公开演示，使用独立 `demo` 账号，不要把演示数据写入自用 `admin`。
- 所有默认密码、JWT secret 必须替换。
- 备份数据库，MinIO 数据按业务需要备份。

## Linux 生产部署

```bash
cd /opt/noto
cp deploy/env.prod.example .env
nano .env

chmod +x deploy/*.sh
./deploy/prod-check.sh --strict
./deploy/prod-up.sh
```

配置 Nginx 和 HTTPS：

```bash
sudo sed 's/noto.example.com/你的域名/g' deploy/nginx/noto.conf \
  | sudo tee /etc/nginx/sites-available/noto
sudo ln -sf /etc/nginx/sites-available/noto /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d 你的域名
```

验证：

```bash
curl https://你的域名/api/v1/health
```

## 生产环境变量

必须修改：

```env
POSTGRES_PASSWORD=改成强密码
MINIO_ROOT_USER=noto_minio
MINIO_ROOT_PASSWORD=改成强密码
NOTO_JWT_SECRET=至少32位随机字符串
NOTO_DEMO_ENABLED=false
```

公开演示账号（可选）：

```env
NOTO_DEMO_ENABLED=true
NOTO_DEMO_USERNAME=demo
NOTO_DEMO_PASSWORD=改成演示账号密码
NOTO_DEMO_EMAIL=demo@noto.local
NOTO_DEMO_NICKNAME=演示账号
NOTO_DEMO_CREATE_USER=true
NOTO_DEMO_RESET_PASSWORD=true
```

说明：

- `admin` 建议留给站长自己使用，并立即修改默认密码。
- `demo` 可对外公开，用于演示预置笔记、待办和提醒。
- 演示种子只会给目标用户写入一次；`NOTO_DEMO_RESET_PASSWORD=true` 会在启动时把独立演示账号密码同步为 `NOTO_DEMO_PASSWORD`。

启用 AI：

```env
NOTO_AI_ENABLED=true
AI_DASHSCOPE_API_KEY=你的Key
```

暂不启用 AI：

```env
NOTO_AI_ENABLED=false
AI_DASHSCOPE_API_KEY=
```

生成随机密钥：

```bash
openssl rand -base64 48
```

## IPv6 家庭服务器

适用：闲置笔记本已安装 Ubuntu Server，IPv6 已开启，能 SSH，Docker 已安装，域名申请中。

飞牛 OS 的实际操作、Docker 国内镜像源、IPv4/IPv6 双栈直连、Cloudflare Tunnel、Lucky / Nginx Proxy Manager 反代、更新和备份见 [FNOS_DOCKER_OPS.md](./FNOS_DOCKER_OPS.md)。

当前飞牛双栈直连端口 80 方案：

```env
FRONTEND_IPV4_HOST=0.0.0.0
FRONTEND_IPV6_HOST=[::]
FRONTEND_PORT=80
```

访问：

```text
http://飞牛局域网IP
http://v6.notoai.cn
```

这种方式不接管飞牛系统 Nginx，飞牛后台继续使用局域网地址 `http://飞牛局域网IP:5666`。如果需要 IPv4 公网访问，推荐叠加 Cloudflare Tunnel。

如果需要让没有 IPv6 的网络也能访问，推荐在飞牛上运行 Cloudflare Tunnel：

```text
https://notoai.cn
    -> Cloudflare
    -> cloudflared
    -> 飞牛本地 Noto
```

该方案不要求家庭宽带具备公网 IPv4，详细步骤见 [FNOS_DOCKER_OPS.md](./FNOS_DOCKER_OPS.md) 的 Cloudflare Tunnel 章节。

前置检查：

```bash
uname -a
lsb_release -a
docker --version
docker compose version
ip -6 addr
ip -6 route
curl -6 https://ifconfig.co
```

外部网络测试：

```bash
ping -6 你的IPv6地址
ssh 用户名@[你的IPv6地址]
```

如果外部不通，依次检查路由器 IPv6 防火墙、Ubuntu UFW、运营商入站限制。

基础加固：

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

建议关闭密码登录前，先确认 SSH 密钥登录可用：

```text
PasswordAuthentication no
PermitRootLogin no
```

域名未生效前，在服务器本机验证：

```bash
curl http://127.0.0.1:8080/api/v1/health
```

域名生效后再配置 Nginx 和 Certbot。

## 运维命令

| 操作 | Linux | Windows 预演 |
|------|-------|--------------|
| 生产预检 | `./deploy/prod-check.sh --strict` | `.\scripts\prod-check.ps1 -Strict` |
| 启动 | `./deploy/prod-up.sh` | `.\scripts\prod-up.ps1` |
| 停止 | `./deploy/prod-down.sh` | `.\scripts\prod-down.ps1` |
| 更新 | `./deploy/prod-update.sh` | `.\scripts\prod-update.ps1` |
| 备份 | `./deploy/prod-backup.sh /var/backups/noto` | `.\scripts\prod-backup.ps1` |
| 日志 | `docker compose logs -f backend` | `docker compose logs -f backend` |
| 飞牛镜像源 | `bash deploy/fnos-docker-mirror.sh` | 不适用 |

## 上线检查清单

- [ ] `prod-check --strict` 通过。
- [ ] `.env` 中演示数据关闭。
- [ ] JWT、数据库、MinIO 密码不是默认值。
- [ ] 安全组 / 防火墙只开放 `22`、`80`、`443`。
- [ ] `https://域名/api/v1/health` 返回 `code: 0`。
- [ ] 登录账号不是默认公开账号。
- [ ] AI 流式问答经 Nginx 正常。
- [ ] 已设置数据库备份。
