# Noto · 知微 — 脚本索引

> Windows PowerShell 脚本，配合 [Docker Desktop](https://www.docker.com/products/docker-desktop/) 与仓库根目录 `docker-compose.yml` 使用。  
> 部署细节见 [docs/DEV_DEPLOY.md](../docs/DEV_DEPLOY.md)。

---

## 快速选择

| 场景 | 命令 |
|------|------|
| **日常本地开发**（IDE 跑前后端） | `.\scripts\dev-up.ps1` → 启动 db + minio |
| **一键演示**（Docker 全栈） | `.\scripts\demo-up.ps1` |
| **演示 + IDE 后端**（仅基础设施） | `.\scripts\demo-up.ps1 -InfraOnly` |
| **查看状态** | `.\scripts\stack-status.ps1` |
| **停止开发基础设施** | `.\scripts\dev-down.ps1` |
| **停止演示栈** | `.\scripts\demo-down.ps1` |
| **生产预检** | `.\scripts\prod-check.ps1` |
| **生产启动**（本机预演） | `.\scripts\prod-up.ps1` |
| **云服务器生产** | `deploy/prod-up.sh`（Linux，见 [deploy/README.md](../deploy/README.md)） |
| **重置演示种子** | `.\scripts\reset-demo-seed.ps1` |
| **导出 OpenAPI** | `.\scripts\export-openapi.ps1` |

---

## 脚本说明

### 开发与基础设施

| 脚本 | 说明 |
|------|------|
| **`dev-up.ps1`** | 启动 `db` + `minio`；自动创建 `.env`；校验 pgvector |
| **`dev-down.ps1`** | 停止 `db` + `minio`（**保留数据卷**） |
| **`start-postgres.ps1`** | 仅启动 PostgreSQL（等价 `compose up -d db`） |
| **`start-minio.ps1`** | 单独拉国内镜像启动 MinIO；**可能与 compose 的 noto-minio 冲突**，优先 `dev-up` |

### 演示栈

| 脚本 | 说明 |
|------|------|
| **`demo-up.ps1`** | 全栈：`db` + `minio` + `backend` + `frontend`；自动释放 9086 端口 |
| **`demo-up.ps1 -InfraOnly`** | 仅 `db` + `minio`，配合 IDE 本地后端（原 `-KeepLocalBackend` 别名仍可用） |
| **`demo-down.ps1`** | `docker compose down`（保留卷） |
| **`demo-down.ps1 -RemoveVolumes`** | 停止并**删除** db/minio 数据卷（慎用） |

### 生产部署

> **云服务器（Linux）** 请用 [`deploy/`](../deploy/README.md) 目录下的 `.sh` 脚本；  
> Windows 下 `scripts/prod-*.ps1` 用于本机预演或与 `deploy/docker-compose.prod.yml` 对齐测试。

| 脚本 | 说明 |
|------|------|
| **`prod-check.ps1`** | `.env` 上线前检查（JWT、弱密码、`NOTO_DEMO_ENABLED`） |
| **`prod-up.ps1`** | 合并 `deploy/docker-compose.prod.yml` 启动；db/minio 不映射公网 |
| **`prod-down.ps1`** | 停止生产栈（`-RemoveVolumes` 删卷） |
| **`prod-update.ps1`** | `git pull` + 重建（Windows 环境） |
| **`prod-backup.ps1`** | PostgreSQL 备份到 `backups/` |

| `deploy/prod-*.sh` | Linux 云服务器等价脚本 + Nginx/HTTPS 指引 → [deploy/README.md](../deploy/README.md) |

### 维护与文档

| 脚本 | 说明 |
|------|------|
| **`reset-demo-seed.ps1`** | 清除 `noto.demo.seeded` 标记，重启 backend 后重新写入 v3 演示数据 |
| **`export-openapi.ps1`** | 从运行中后端导出 `docs/openapi.yaml`（需 dev profile + springdoc） |
| **`stack-status.ps1`** | 容器列表、端口占用、健康检查摘要 |

### 共享库

| 路径 | 说明 |
|------|------|
| **`lib/Noto-Docker.ps1`** | 公共函数：`.env` 读取、compose 启动、pgvector、健康等待等 |

---

## 典型工作流

### A. IDE 开发（推荐）

```powershell
cd E:\Noto_知微
.\scripts\dev-up.ps1
cd backend; .\dev.ps1 -WatchCompile    # 或 VS Code「backend: dev」
cd frontend; npm run dev               # http://localhost:5173
```

### B. Docker 演示

```powershell
.\scripts\demo-up.ps1
# 浏览器 http://localhost:8080  登录 admin / admin123
.\scripts\demo-down.ps1              # 结束演示
```

### C. 重新写入演示数据

```powershell
.\scripts\reset-demo-seed.ps1
# Docker 演示：docker compose restart backend
# IDE 开发：重启 backend 进程
```

### D. 发版前导出 API 契约

```powershell
# 先启动 dev 后端
.\scripts\export-openapi.ps1
```

### E. 生产部署（云服务器）

```bash
# Linux VPS — 详见 deploy/README.md
cp deploy/env.prod.example .env && nano .env
./deploy/prod-check.sh --strict
./deploy/prod-up.sh
# 再配置 deploy/nginx/noto.conf + certbot
```

Windows 本机预演生产 compose：

```powershell
copy deploy\env.prod.example .env   # 编辑密钥
.\scripts\prod-check.ps1 -Strict
.\scripts\prod-up.ps1
```

---

## 三种环境对比

| | 开发 | 演示 | 生产 |
|--|------|------|------|
| 脚本 | `dev-up.ps1` | `demo-up.ps1` | `deploy/prod-up.sh` |
| 后端 | IDE / dev profile | Docker prod | Docker prod |
| 对外端口 | 5432/9000/9086/5173 | localhost 全映射 | **仅 127.0.0.1:8080** + Nginx 443 |
| 演示数据 | 可选 | 默认开 | **必须关** |

---

## 端口与账号

| 服务 | 地址 | 默认账号 |
|------|------|----------|
| 后端 API | http://localhost:9086 | — |
| Vite 前端 | http://localhost:5173 | — |
| Docker 前端 | http://localhost:8080 | admin / admin123 |
| PostgreSQL | localhost:5432 | 见 `.env` |
| MinIO 控制台 | http://localhost:9001 | minioadmin / minioadmin |

---

## VS Code 任务

`.vscode/tasks.json` 已引用：

- `backend: reset demo seed` → `reset-demo-seed.ps1`
- `backend: dev (演示数据)` → 先 reset seed 再启动 backend

---

## 故障排查

| 现象 | 处理 |
|------|------|
| 9086 被占用 | `.\scripts\stack-status.ps1` 查 PID；演示前 `demo-up` 会自动释放 |
| pgvector 警告 | `docker compose down db; docker rm -f noto-db; .\scripts\dev-up.ps1` |
| MinIO 拉取失败 | 用 `start-minio.ps1`（国内镜像）或配置 Docker 镜像加速 |
| 演示数据未更新 | `reset-demo-seed.ps1` 后重启 backend |
| OpenAPI 导出失败 | 确认 backend 以 `dev` profile 运行且 `/swagger-ui.html` 可访问 |
