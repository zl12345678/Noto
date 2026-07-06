# Noto · 知微 — 生产部署

> **云服务器（Linux）用本目录脚本**；Windows 本地可用 `scripts/prod-*.ps1` 做预演。  
> 完整说明见 [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md)。

---

## 架构

```text
Internet :443
    ↓
宿主机 Nginx（HTTPS，deploy/nginx/noto.conf）
    ↓ 127.0.0.1:8080
noto-frontend（容器）
    ↓ Docker 内网
noto-backend / noto-db / noto-minio（不映射公网端口）
```

---

## 快速开始（Ubuntu / Debian 云服务器）

```bash
cd /opt/noto   # git clone 后的目录

# 1. 配置环境
cp deploy/env.prod.example .env
nano .env      # 必改 JWT、数据库密码、MinIO 密码；NOTO_DEMO_ENABLED=false

# 2. 上线前检查
chmod +x deploy/*.sh
./deploy/prod-check.sh --strict

# 3. 启动应用栈
./deploy/prod-up.sh

# 4. 配置 Nginx + HTTPS
sudo sed 's/noto.example.com/你的域名/g' deploy/nginx/noto.conf \
  | sudo tee /etc/nginx/sites-available/noto
sudo ln -sf /etc/nginx/sites-available/noto /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d 你的域名
```

---

## 脚本索引

| 脚本 | 说明 |
|------|------|
| **`prod-check.sh`** | `.env` 安全检查（JWT 长度、弱密码、演示开关） |
| **`prod-up.sh`** | 合并 `docker-compose.prod.yml` 启动全栈 |
| **`prod-down.sh`** | 停止栈（`--volumes` 删除数据卷） |
| **`prod-update.sh`** | `git pull` + 重建 |
| **`prod-backup.sh`** | `pg_dump` 备份到 `backups/` 或指定目录 |

Windows 等价脚本在 [`scripts/`](../scripts/README.md)：`prod-check.ps1`、`prod-up.ps1` 等。

---

## 配置文件

| 文件 | 用途 |
|------|------|
| [`env.prod.example`](env.prod.example) | 生产 `.env` 模板 |
| [`docker-compose.prod.yml`](docker-compose.prod.yml) | 端口绑定：仅 127.0.0.1，db/minio 不对外 |
| [`nginx/noto.conf`](nginx/noto.conf) | 宿主机 Nginx：HTTPS、SSE 流式 AI |

---

## 上线检查清单

- [ ] `NOTO_JWT_SECRET` ≥ 32 位随机字符
- [ ] `POSTGRES_PASSWORD`、`MINIO_ROOT_PASSWORD` 已改为强密码
- [ ] `NOTO_DEMO_ENABLED=false`
- [ ] 安全组仅开放 **22 / 80 / 443**（不要开放 5432、9000、9086）
- [ ] 不使用 `admin` 作为对外演示账号；演示数据应写入独立 `demo` 账号
- [ ] `https://域名/api/v1/health` 返回 `code: 0`
- [ ] AI 流式问答（SSE）经 Nginx 正常
- [ ] 配置定时备份：`deploy/prod-backup.sh` + cron

---

## 日常运维

```bash
# 查看日志
docker compose logs -f backend

# 更新发版
./deploy/prod-update.sh

# 数据库备份
./deploy/prod-backup.sh /var/backups/noto

# 仅重启后端（改 .env 后）
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml restart backend
```

---

## 与开发/演示的区别

| 维度 | 开发 `scripts/dev-up` | 演示 `scripts/demo-up` | **生产 `deploy/prod-up`** |
|------|----------------------|------------------------|---------------------------|
| Profile | `dev`（IDE） | `prod`（compose） | `prod` |
| 端口 | db/minio 映射本机 | 全端口映射 localhost | **仅 127.0.0.1:8080** |
| 演示数据 | 可选 | 默认开启 | **必须关闭** |
| 对外访问 | 本机 | 本机 :8080 | **Nginx HTTPS** |
| Swagger | 开启 | 关闭 | 关闭 |
