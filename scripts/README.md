# Noto · 知微 — 脚本索引

> **日常只需看 [docs/COMMANDS.md](../docs/COMMANDS.md)** — 按场景（开发 / 演示 / 生产 / APK）列出该执行的命令。  
> 本文档是完整脚本清单，备查用。

---

## 场景 → 脚本（速查）

| 场景 | 命令 |
|------|------|
| 日常开发 | `dev-up.ps1` → IDE 启前后端 → `dev-down.ps1` |
| Docker 演示 | `demo-up.ps1` → `demo-down.ps1` |
| 生产（Windows 预演） | `prod-check.ps1 -Strict` → `prod-up.ps1` |
| 生产（Linux 云服务器） | `deploy/prod-up.sh` |
| Android APK | `build-apk.ps1`（首次加 `-SetupSdk`） |
| 查状态 | `stack-status.ps1` |

---

## 全部脚本

| 脚本 | 说明 |
|------|------|
| `dev-up.ps1` | 启动 db + minio |
| `dev-down.ps1` | 停止 db + minio（保留卷） |
| `demo-up.ps1` | Docker 全栈演示；`-InfraOnly` 仅 db+minio |
| `demo-down.ps1` | 停止演示栈；`-RemoveVolumes` 删卷 |
| `prod-check.ps1` | 生产 `.env` 预检 |
| `prod-up.ps1` | 启动生产 compose |
| `prod-down.ps1` | 停止生产栈 |
| `prod-update.ps1` | git pull + 重建 |
| `prod-backup.ps1` | PostgreSQL 备份 |
| `build-apk.ps1` | Android 一键打包；`-SetupSdk` / `-Install` / `-Release` |
| `reset-demo-seed.ps1` | 重置演示种子 |
| `export-openapi.ps1` | 导出 `docs/openapi.yaml` |
| `stack-status.ps1` | 容器与端口状态 |
| `start-postgres.ps1` | 仅 db（一般用 `dev-up` 即可） |
| `start-minio.ps1` | 仅 minio（国内镜像；可能与 compose 冲突） |

共享库：`lib/Noto-Docker.ps1`、`lib/Noto-Android.ps1`

Linux 等价脚本见 `deploy/*.sh` → [deploy/README.md](../deploy/README.md)

---

## 端口与账号

| 服务 | 地址 | 账号 |
|------|------|------|
| 前端（开发） | http://localhost:5173 | — |
| 前端（演示） | http://localhost:8080 | admin / admin123 |
| 后端 API | http://localhost:9086 | — |
| MinIO 控制台 | http://localhost:9001 | minioadmin / minioadmin |

详细说明 → [docs/COMMANDS.md](../docs/COMMANDS.md)
