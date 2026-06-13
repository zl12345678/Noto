# Noto · 知微 — 开发与部署指南

> 本文梳理 **本地开发** 与 **Docker 演示/生产部署** 的完整流程。  
> 快速功能说明见 [README.md](../README.md)，发版前自测见 [E2E_CHECKLIST.md](./E2E_CHECKLIST.md)。

---

## 1. 环境总览

Noto 采用前后端分离架构，依赖 PostgreSQL（pgvector）与 MinIO（附件/图片）。

```text
┌─────────────────────────────────────────────────────────────────┐
│                        本地开发（IDE）                           │
├─────────────────────────────────────────────────────────────────┤
│  浏览器 :5173  →  Vite dev server  →  proxy /api → :9086       │
│  Spring Boot (profile=dev)  →  localhost:5432 / localhost:9000  │
│  基础设施：Docker 仅跑 db + minio（scripts/dev-up.ps1）          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                   Docker 全栈（演示 / 生产）                      │
├─────────────────────────────────────────────────────────────────┤
│  浏览器 :8080  →  nginx (frontend)  →  proxy /api → backend    │
│  backend (profile=prod)  →  db:5432 / minio:9000（compose 内网） │
│  一键脚本：scripts/demo-up.ps1 或 docker compose up -d --build   │
└─────────────────────────────────────────────────────────────────┘
```

### 1.1 开发 vs 部署对比

| 维度 | 本地开发 | Docker 演示/生产 |
|------|----------|------------------|
| **典型用途** | 日常编码、调试、热重载 | 演示、验收、单机部署 |
| **前端** | Vite `npm run dev` → **5173** | Nginx 静态资源 → **8080** |
| **后端** | IDE / `mvnw spring-boot:run` → **9086** | 容器内 JAR → **9086** |
| **Spring Profile** | `dev`（默认） | `prod`（compose 注入） |
| **数据库** | Docker `noto-db`，库名 **`noto_zhihui_dev`** | 同左或 `.env` 中 `noto_zhihui` |
| **MinIO** | Docker `noto-minio` | compose 内 `minio` 服务 |
| **API 代理** | Vite `vite.config.ts` | Nginx `frontend/docker/nginx.conf` |
| **演示数据** | 可选 `$env:NOTO_DEMO_ENABLED="true"` | 默认 `NOTO_DEMO_ENABLED=true` |
| **AI** | `application-dev.yml` 或环境变量 | `.env` 中 `NOTO_AI_ENABLED` + Key |

---

## 2. 前置要求

### 2.1 通用

| 组件 | 版本要求 |
|------|----------|
| JDK | 17+ |
| Maven | 3.9+（项目自带 `backend/mvnw`） |
| Node.js | 18+（推荐 20） |
| Docker Desktop | 含 Compose v2（Windows 推荐） |

### 2.2 端口占用

| 端口 | 服务 | 环境 |
|------|------|------|
| 5173 | 前端 Vite | 仅本地开发 |
| 8080 | 前端 Nginx | Docker 全栈 |
| 9086 | 后端 API | 开发与 Docker 共用，**不可同时占用** |
| 5432 | PostgreSQL | Docker |
| 9000 / 9001 | MinIO API / 控制台 | Docker |

> **注意**：若 9086 已被本地后端占用，运行 `demo-up.ps1` 会自动结束该进程；保留本地后端时用 `demo-up.ps1 -InfraOnly` 或 `dev-up.ps1`（只启 db/minio）。

---

## 3. 首次准备（两种环境共用）

在项目根目录执行：

```powershell
cd e:\Noto_知微

# 1. 复制环境变量模板（db 密码、JWT、AI 等）
copy .env.example .env

# 2. 按需编辑 .env（见第 6 节变量说明）
notepad .env
```

`.env` 会被 `docker compose` 读取；本地 JDBC 默认与 `application-dev.yml` 对齐：

- 库名：`noto_zhihui_dev`
- 用户：`postgres`
- 密码：`.env` 中 `POSTGRES_PASSWORD`（示例为 `123456`）

数据库 schema 来源：

1. 首次启动 PostgreSQL 时挂载 `建表SQL.sql` 初始化
2. 后端启动时 `DatabaseSchemaMigrator` 自动补丁（含 pgvector 列、RAG 索引等）

---

## 4. 本地开发环境

### 4.1 推荐流程（四步）

```powershell
# 步骤 1：启动基础设施（PostgreSQL pgvector + MinIO）
.\scripts\dev-up.ps1

# 步骤 2：启动后端
cd backend
.\mvnw.cmd spring-boot:run
# 或使用 VS Code / Cursor：运行配置「Backend (DevTools 热部署)」

# 步骤 3：启动前端
cd ..\frontend
npm install          # 首次
npm run dev

# 步骤 4：验证
# 健康检查：http://localhost:9086/api/v1/health  → code: 0
# 前端页面：http://localhost:5173
```

### 4.2 基础设施脚本说明

| 脚本 | 作用 |
|------|------|
| **`scripts/dev-up.ps1`** | **推荐**：`docker compose up -d db minio`，校验 pgvector |
| `scripts/dev-down.ps1` | 停止 db + minio（保留数据卷） |
| `scripts/start-postgres.ps1` | 仅启动数据库（等价 `compose up -d db`） |
| `scripts/start-minio.ps1` | 单独启动 MinIO（国内镜像拉取；与 compose 容器可能冲突，优先 dev-up） |
| `scripts/stack-status.ps1` | 容器、端口、健康检查摘要 |

`dev-up.ps1` 完成后会输出下一步提示与容器状态。

### 4.3 后端开发

**方式 A：命令行**

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"   # 默认已是 dev
.\mvnw.cmd spring-boot:run
```

**方式 B：IDE（推荐）**

- 运行 **`Backend (DevTools 热部署)`**（`.vscode/launch.json`）
- 修改 Java 后保存 → 自动 `compile` → DevTools 重启（约 1–3 秒）
- 调试端口：`5005`（`-Plocal-debug` profile）

**方式 C：仅编译触发热重载**

```powershell
cd backend
.\mvnw.cmd -q compile -DskipTests
# 或 touch backend/.reloadtrigger
```

**健康检查**

```text
GET http://localhost:9086/api/v1/health
→ {"code":0,"data":{"status":"UP"},...}
```

**API 文档（dev profile）**

| 地址 | 说明 |
|------|------|
| http://localhost:9086/swagger-ui.html | Swagger UI 交互文档 |
| http://localhost:9086/v3/api-docs.yaml | OpenAPI YAML |

导出到仓库：`.\scripts\export-openapi.ps1` → `docs/openapi.yaml`

**默认 dev 账号**（首次启动自动创建）：`admin` / `admin123`

### 4.4 前端开发

```powershell
cd frontend
npm install
npm run dev
```

- 访问：**http://localhost:5173**
- API 代理：`/api` → `http://localhost:9086`（见 `frontend/vite.config.ts`）
- VS Code 任务：**`frontend: dev`**

### 4.5 本地启用 MinIO（图片上传）

1. `dev-up.ps1` 已启动 MinIO，或 `noto.minio.enabled=true`（`application-dev.yml` 默认已开）
2. 控制台：http://localhost:9001（`minioadmin` / `minioadmin`）
3. 重启后端后，编辑器工具栏可上传图片（需先保存文档获得 `noteId`）

### 4.6 本地启用 AI（可选）

**方式 A：环境变量（推荐，避免密钥进仓库）**

```powershell
$env:NOTO_AI_ENABLED = "true"
$env:AI_DASHSCOPE_API_KEY = "你的百炼/通义 API Key"
cd backend
.\mvnw.cmd spring-boot:run
```

**方式 B：修改 `application-dev.yml`**

```yaml
noto:
  ai:
    enabled: true
    model: qwen-turbo
    api-key: 你的密钥
```

验证：`GET http://localhost:9086/api/v1/ai/status` → `enabled: true`

RAG 向量检索需 pgvector 扩展（`dev-up.ps1` 会自动 `CREATE EXTENSION vector`）。

### 4.7 本地预置演示数据

**dev 环境默认开启**（`application-dev.yml` → `noto.demo.enabled: true`）。Cursor / VS Code 任务 **`backend: dev`** 也会设置 `NOTO_DEMO_ENABLED=true`。

首次写入 v2 种子后会在 `user_setting` 打标记；**截止日期每次启动自动刷新**。

**重新写入完整演示数据**（清除标记后重启）：

```powershell
.\scripts\reset-demo-seed.ps1
# 然后重启 backend
```

或在 Cursor 中运行任务：**`backend: dev (演示数据)`**（会先 reset 再启动）。

启动后在 backend 日志中应看到：

```text
Demo data seed starting for admin (v3, target 74 notes)...
Demo data v3 seeded for admin: 74 notes, 6 folders, 20 todos ...
```

若只有 `already present (skipping seed)`，说明 v2 已写入过，需先 reset。

关闭演示种子：任务 **`backend: dev (no demo)`**，或 `$env:NOTO_DEMO_ENABLED="false"`。

### 4.8 本地开发停止

```powershell
# 停止后端：Ctrl+C 或 VS Code 停止调试
# 停止前端：Ctrl+C

# 停止 Docker 基础设施（保留数据卷）
docker compose stop db minio

# 或停止全部 compose 服务
docker compose down
```

---

## 5. Docker 演示 / 生产部署

### 5.1 三种启动方式

| 方式 | 命令 | 适用场景 |
|------|------|----------|
| **一键演示（推荐）** | `.\scripts\demo-up.ps1` | 演示、新人体验、含预置数据 |
| **手动 Compose** | `docker compose up -d --build` | 自定义步骤、CI/CD 参考 |
| **仅基础设施 + 本地后端** | `.\scripts\dev-up.ps1` + IDE 后端 | 开发调试 Docker 中的 db/minio |

### 5.2 一键演示流程

```powershell
cd e:\Noto_知微

# 确保 .env 存在（脚本会自动从 .env.example 复制）
.\scripts\demo-up.ps1
```

脚本会依次：

1. 若 9086 被占用且未使用 `-InfraOnly`，结束本地后端进程
2. 启动 / 修复 `noto-db`、`noto-minio`
3. `docker compose up -d --build backend frontend`
4. 等待健康检查，检查演示数据种子日志

**访问地址**

| 服务 | URL |
|------|-----|
| Web UI | http://localhost:8080 |
| API 健康检查 | http://localhost:9086/api/v1/health |
| MinIO 控制台 | http://localhost:9001 |
| PostgreSQL | localhost:5432 |

**演示账号**：`admin` / `admin123`

**预置内容**（`NOTO_DEMO_ENABLED=true` 时，**v3** 种子仅首次写入；待办截止日期每次启动自动刷新）：

- 分组 6 个：工作笔记、项目资料、个人学习、会议纪要、灵感碎片、参考资料
- 文档 **74 篇**（周会系列、工作日志、PRD、剪藏、子文档等，编辑时间分布近三个月）
- 待办 20 条、标签 8 个、提醒 2 条

### 5.3 生产部署检查清单

上线前请在 `.env` 中至少修改：

```env
# 必改
NOTO_JWT_SECRET=随机长字符串至少32字符
POSTGRES_PASSWORD=强密码

# 按需
NOTO_DEMO_ENABLED=false          # 生产关闭演示种子
NOTO_AI_ENABLED=true
AI_DASHSCOPE_API_KEY=...
MINIO_ROOT_PASSWORD=强密码
```

部署步骤：

```powershell
cd e:\Noto_知微
copy deploy\env.prod.example .env
# 编辑 .env 完成上述修改

# 推荐：预检 + 生产 compose（合并 deploy/docker-compose.prod.yml）
.\scripts\prod-check.ps1 -Strict
.\scripts\prod-up.ps1

# 云服务器 Linux：
#   ./deploy/prod-check.sh --strict && ./deploy/prod-up.sh
# 详见 deploy/README.md
```

**生产 profile 行为**（`application-prod.yml` + compose 环境变量）：

- 数据源：`jdbc:postgresql://db:5432/${POSTGRES_DB}`
- JWT：`NOTO_JWT_SECRET`
- MinIO：默认启用（`NOTO_MINIO_ENABLED=true`），endpoint 为容器内 `http://minio:9000`
- 日志：`root: warn`，业务 `info`

### 5.4 数据持久化

Compose 使用命名卷（与项目名 `noto` 组合）：

| 卷 | 用途 |
|----|------|
| `noto_postgres_data` | PostgreSQL 数据 |
| `noto_minio_data` | MinIO 对象存储 |

```powershell
# 停止容器（保留数据）
docker compose down

# 停止并删除数据卷（慎用，会清空库与附件）
docker compose down -v
```

若出现 volume「非 compose 创建」警告，不影响运行；可忽略或在 `docker-compose.yml` 中将卷标为 `external: true`。

### 5.5 重新写入演示数据

```powershell
docker exec noto-db psql -U postgres -d noto_zhihui_dev -c "DELETE FROM user_setting WHERE setting_key='noto.demo.seeded';"
docker compose restart backend
docker logs noto-backend | findstr "Demo data seeded"
```

### 5.6 常用运维命令

```powershell
docker compose logs -f backend      # 后端日志
docker compose logs -f frontend     # Nginx 访问/错误
docker compose restart backend      # 重启后端（改 .env 后）
docker compose up -d --build backend   # 仅重建后端
docker ps --filter "name=noto-"     # 容器状态
```

### 5.7 云服务器部署（HTTPS + 反向代理）

适用于阿里云 / 腾讯云 / AWS 等 **单机 VPS**：Docker Compose 跑应用栈，**宿主机 Nginx** 终止 TLS，只对外开放 80/443。

#### 架构

```text
Internet :443
    ↓
宿主机 Nginx（Let's Encrypt 证书）
    ↓ 127.0.0.1:8080
noto-frontend（容器 Nginx，反代 /api → backend）
    ↓ Docker 内网
noto-backend / noto-db / noto-minio（不映射公网端口）
```

#### 5.7.1 服务器要求

| 项 | 建议 |
|----|------|
| 系统 | Ubuntu 22.04 / Debian 12 |
| 配置 | 2 vCPU · 4 GB 内存 · 40 GB 磁盘 |
| 软件 | Docker Engine + Compose v2、Nginx、Certbot |
| 域名 | `noto.example.com` A 记录指向服务器公网 IP |
| 安全组 | 入站仅 **22**（SSH）、**80**、**443**；**不要**开放 5432 / 9000 / 9086 |

#### 5.7.2 部署步骤

**1. 安装 Docker 与 Nginx**

```bash
# Docker（官方脚本）
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# 重新登录后生效

sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx git
```

**2. 拉取代码并配置环境**

```bash
git clone <你的仓库地址> /opt/noto
cd /opt/noto

cp deploy/env.prod.example .env
nano .env   # 修改 JWT、数据库密码、MinIO 密码、AI Key；NOTO_DEMO_ENABLED=false
# 生产 compose 合并文件：deploy/docker-compose.prod.yml（需 Compose 2.24+，见 §5.7.6）
```

**3. 启动应用栈**

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml up -d --build
docker compose ps
curl -s http://127.0.0.1:8080/api/v1/health   # 应返回 code: 0
```

**4. 配置 Nginx + HTTPS**

```bash
# 将示例中的 noto.example.com 替换为你的域名
sudo sed 's/noto.example.com/你的域名/g' deploy/nginx/noto.conf \
  | sudo tee /etc/nginx/sites-available/noto

sudo ln -sf /etc/nginx/sites-available/noto /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 自动申请并配置 Let's Encrypt（按提示输入邮箱）
sudo certbot --nginx -d 你的域名
```

**5. 验收**

- 浏览器访问 `https://你的域名` → 注册/登录页
- `https://你的域名/api/v1/health` → `{"code":0,...}`
- 上传图片、AI 流式问答（SSE）是否正常

#### 5.7.3 仓库内配置文件

| 文件 | 用途 |
|------|------|
| [deploy/nginx/noto.conf](../deploy/nginx/noto.conf) | 宿主机 Nginx：HTTPS、SSE、上传大小 |
| [deploy/docker-compose.prod.yml](../deploy/docker-compose.prod.yml) | 生产端口绑定（与主 compose 合并） |
| [deploy/env.prod.example](../deploy/env.prod.example) | 云服务器 `.env` 模板 |

#### 5.7.4 备份与更新

**数据库备份（cron 示例）**

```bash
# /etc/cron.daily/noto-pg-backup
docker exec noto-db pg_dump -U postgres noto_zhihui | gzip > /var/backups/noto-$(date +%F).sql.gz
find /var/backups -name 'noto-*.sql.gz' -mtime +14 -delete
```

**MinIO**：定期备份卷 `noto_minio_data`，或使用 `mc mirror` 同步到对象存储。

**应用更新**

```bash
cd /opt/noto
git pull
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml up -d --build
docker compose logs -f backend   # 确认 Started 且无 ERROR
```

**证书续期**：Certbot 默认有 systemd timer；可执行 `sudo certbot renew --dry-run` 验证。

#### 5.7.5 可选：Caddy 一键 HTTPS

若不想手动维护 Nginx 证书，可用 [Caddy](https://caddyserver.com/)：

```caddy
noto.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

Caddy 自动申请/续期证书；SSE 与上传需在 Caddyfile 中增加 `flush_interval -1`、适当 `request_body` 限制。

#### 5.7.6 安全提醒与 Compose 兼容

- **务必**修改 `NOTO_JWT_SECRET`、`POSTGRES_PASSWORD`、`MINIO_ROOT_PASSWORD`
- 生产关闭 `NOTO_DEMO_ENABLED`，不要使用默认 `admin/admin123` 作为对外账号
- MinIO 控制台（9001）**不要**对公网开放；需要时在 SSH 隧道内访问：`ssh -L 9001:127.0.0.1:9001 user@server`
- 定期 `apt upgrade`、关注 Docker 镜像安全更新

**Compose 版本较旧（不支持 `ports: !reset`）时**：手动编辑 `docker-compose.yml`——删除 `db`、`minio` 的 `ports` 段；将 `frontend`、`backend` 的端口改为 `127.0.0.1:8080:80` 与 `127.0.0.1:9086:9086`，再执行 `docker compose up -d --build`。

---

## 6. 环境变量参考

根目录 `.env`（供 `docker compose` 使用）：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `POSTGRES_DB` | `noto_zhihui_dev` | 数据库名 |
| `POSTGRES_USER` | `postgres` | 数据库用户 |
| `POSTGRES_PASSWORD` | 见 `.env.example` | 须与 JDBC 一致 |
| `POSTGRES_PORT` | `5432` | 宿主机映射端口 |
| `NOTO_JWT_SECRET` | 示例占位 | **生产必改** |
| `NOTO_AI_ENABLED` | `false` | 是否启用 AI |
| `AI_DASHSCOPE_API_KEY` | 空 | 通义/百炼 API Key |
| `NOTO_DEMO_ENABLED` | `true` | Docker 演示种子开关 |
| `NOTO_MINIO_ENABLED` | `true` | 附件存储（compose 默认开） |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | `minioadmin` | MinIO 凭证 |
| `BACKEND_PORT` | `9086` | 后端宿主机端口 |
| `FRONTEND_PORT` | `8080` | 前端宿主机端口 |

后端还支持（见 `application-prod.yml`）：

| 变量 | 说明 |
|------|------|
| `SPRING_DATASOURCE_URL` | 完整 JDBC（compose 自动注入） |
| `NOTO_AI_MODEL` | 模型名，默认 `qwen-plus` |
| `NOTO_AI_RAG_ENABLED` | RAG 开关 |
| `NOTO_MINIO_ENDPOINT` | MinIO 地址（容器内 `http://minio:9000`） |

---

## 7. 脚本与 Compose 服务索引

### 7.1 脚本

| 路径 | 说明 |
|------|------|
| [`scripts/README.md`](../scripts/README.md) | **脚本总索引**（推荐先看） |
| `scripts/dev-up.ps1` | 本地开发：db + minio |
| `scripts/dev-down.ps1` | 停止 db + minio（保留卷） |
| `scripts/demo-up.ps1` | 全栈演示：db + minio + backend + frontend |
| `scripts/demo-up.ps1 -InfraOnly` | 仅 db + minio，配合 IDE 本地后端 |
| `scripts/demo-down.ps1` | 停止全栈（`docker compose down`） |
| `scripts/start-postgres.ps1` | 仅 PostgreSQL |
| `scripts/start-minio.ps1` | 仅 MinIO（国内镜像） |
| `scripts/reset-demo-seed.ps1` | 清除演示种子标记，重启 backend 后重写入 |
| `scripts/export-openapi.ps1` | 导出 `docs/openapi.yaml` |
| `scripts/stack-status.ps1` | 容器与端口状态 |
| `scripts/prod-check.ps1` | 生产 `.env` 预检（Windows） |
| `scripts/prod-up.ps1` | 生产 compose 启动（Windows 预演） |
| `scripts/prod-down.ps1` | 停止生产栈 |
| `scripts/prod-update.ps1` | 拉代码并重建 |
| `scripts/prod-backup.ps1` | 数据库备份 |
| [`deploy/README.md`](../deploy/README.md) | **云服务器生产脚本**（`prod-*.sh`、Nginx、HTTPS） |

### 7.2 Compose 服务

| 服务 | 镜像/构建 | 容器名 |
|------|-----------|--------|
| `db` | `pgvector/pgvector:pg16` | `noto-db` |
| `minio` | `minio/minio:RELEASE.2024-12-18...` | `noto-minio` |
| `backend` | `backend/Dockerfile` 多阶段构建 | `noto-backend` |
| `frontend` | `frontend/Dockerfile` → nginx | `noto-frontend` |

---

## 8. 测试与 CI

**本地集成测试**（需 Docker，Testcontainers 拉起 PostgreSQL）：

```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\mvnw.cmd test
```

**CI**（`.github/workflows/ci.yml`）：push/PR 时执行 `backend` 测试 + `frontend` 生产构建。

**手动 E2E**：见 [E2E_CHECKLIST.md](./E2E_CHECKLIST.md)（约 15 分钟）。

---

## 9. 常见问题

### Q1：`demo-up.ps1` 与本地后端端口冲突

- 现象：9086 已被占用  
- 处理：关闭 IDE 后端，或 `demo-up.ps1 -InfraOnly` / `dev-up.ps1`（只用 Docker 的 db/minio）

### Q2：RAG 未使用 pgvector

- 现象：日志无 `RAG pgvector enabled`  
- 处理：确认 `noto-db` 镜像含 pgvector → `docker inspect noto-db` 查看 Image  
- 修复：`docker compose down db; docker rm -f noto-db; docker compose up -d db`，再跑 `dev-up.ps1`

### Q3：MinIO 容器名冲突

- 现象：`noto-minio` 已存在但非 compose 创建  
- 处理：`docker rm -f noto-minio` 后重新 `dev-up.ps1` 或 `demo-up.ps1`

### Q4：前端 5173 能开但 API 403

- 检查后端是否启动、Vite proxy 是否指向 9086  
- Docker 环境访问 **8080**（非 5173）

### Q5：演示数据未出现

- 确认 `NOTO_DEMO_ENABLED=true` 且 backend 日志有 `Demo data seeded`  
- 若已种子过，按 [5.5](#55-重新写入演示数据) 清除标记后重启

### Q6：Compose volume 警告

```text
volume "noto_postgres_data" already exists but was not created by Docker Compose
```

- 不影响运行；数据卷由早期手动或旧 compose 创建  
- 可忽略，或将卷在 `docker-compose.yml` 中声明为 `external: true`

---

## 10. 快速决策树

```text
我要写代码调试？
  └─ dev-up.ps1 → backend (IDE) → frontend npm run dev → :5173

我要给别人演示？
  └─ demo-up.ps1 → :8080 → admin/admin123

我要单机上线（本机 Docker）？
  └─ 改 .env（JWT/密码/关 DEMO）→ docker compose up -d --build → :8080

我要云服务器 HTTPS 上线？
  └─ 见 docs/DEV_DEPLOY.md §5.7 → override 绑 127.0.0.1 → 宿主机 Nginx + certbot

我只要数据库，后端在 IDE？
  └─ dev-up.ps1 → spring-boot:run → :5173 + :9086
```

---

## 11. 相关文档

| 文档 | 内容 |
|------|------|
| [README.md](../README.md) | 功能、API、路线图 |
| [E2E_CHECKLIST.md](./E2E_CHECKLIST.md) | 15 分钟回归清单 |
| [MULTI_PLATFORM.md](./MULTI_PLATFORM.md) | 多端路线图与移动 Web MVP |
| [docker-compose.yml](../docker-compose.yml) | 服务定义 |
| [deploy/nginx/noto.conf](../deploy/nginx/noto.conf) | 云服务器 Nginx + HTTPS 示例 |
| [deploy/env.prod.example](../deploy/env.prod.example) | 云服务器 `.env` 模板 |
