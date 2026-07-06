# Noto 开发指南

> 本文合并原新人指南、命令速查和 E2E 检查清单。开发时优先看这一份。

## 目录速览

| 目录 | 作用 |
|------|------|
| `backend/` | Spring Boot 后端：REST API、JWT、AI、搜索、附件、演示数据 |
| `frontend/` | Vue 3 Web 前端：桌面端和移动 Web 适配 |
| `mobile/` | uni-app 移动客户端 |
| `scripts/` | Windows PowerShell 开发、演示、生产预演、构建脚本 |
| `deploy/` | Linux 生产部署脚本和 Nginx 配置 |

## 环境要求

- JDK 17
- Node.js 18+
- Docker Desktop / Docker Engine
- Maven wrapper 使用 `backend/mvnw.cmd` 或 `backend/mvnw`

## 日常开发

本地开发推荐三个终端：基础设施、后端、前端。

```powershell
# 1. 仓库根目录：PostgreSQL + MinIO
.\scripts\dev-up.ps1

# 2. 后端
cd backend
.\dev.ps1 -WatchCompile

# 3. 前端
cd frontend
npm install
npm run dev
```

访问：

| 服务 | 地址 |
|------|------|
| Web 前端 | http://localhost:5173 |
| 后端健康检查 | http://localhost:9086/api/v1/health |
| MinIO 控制台 | http://localhost:9001 |

默认演示账号：`demo` / `123456`。

停止：

```powershell
# 前后端终端 Ctrl+C
.\scripts\dev-down.ps1
```

## Docker 演示

适合不启 IDE，直接给别人看完整系统。

```powershell
.\scripts\demo-up.ps1
# 浏览器打开 http://localhost:8080
.\scripts\demo-down.ps1
```

只启动数据库和 MinIO，后端仍用 IDE：

```powershell
.\scripts\demo-up.ps1 -InfraOnly
```

## 常用脚本

| 场景 | 命令 |
|------|------|
| 开发基础设施 | `.\scripts\dev-up.ps1` / `.\scripts\dev-down.ps1` |
| Docker 演示 | `.\scripts\demo-up.ps1` / `.\scripts\demo-down.ps1` |
| 生产预检 | `.\scripts\prod-check.ps1 -Strict` |
| Windows 生产预演 | `.\scripts\prod-up.ps1` / `.\scripts\prod-down.ps1` |
| 更新生产栈 | `.\scripts\prod-update.ps1` |
| 数据库备份 | `.\scripts\prod-backup.ps1` |
| 移动端构建 | `.\scripts\build-apk.ps1` |
| 查看状态 | `.\scripts\stack-status.ps1` |
| 重置演示种子 | `.\scripts\reset-demo-seed.ps1` |
| 导出 OpenAPI | `.\scripts\export-openapi.ps1` |

## 启用 AI

PowerShell 环境变量方式：

```powershell
$env:NOTO_AI_ENABLED = "true"
$env:AI_DASHSCOPE_API_KEY = "你的百炼/通义 API Key"
```

后端启动后访问：

```text
GET http://localhost:9086/api/v1/ai/status
```

期望 `enabled: true`。如果不启用 AI，普通笔记、待办、搜索、提醒仍可使用。

## 后端验证

```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\mvnw.cmd -q -DskipTests compile
```

## 前端验证

```powershell
cd frontend
npm run build:prod
```

说明：当前 `npm run build` 包含 `vue-tsc -b`，仓库存在既有依赖声明和 AxiosResponse 类型债；发版打包以 `npm run build:prod` 验证 Vite 构建。

## 演示前 E2E 检查

约 15 分钟跑完一遍。

- 登录演示账号：`demo` / `123456`
- 健康检查：`GET /api/v1/health` 返回 `code: 0`
- 可选 AI：`GET /api/v1/ai/status` 返回 `enabled: true`
- 新建笔记，保存后刷新仍存在
- Markdown 阅读模式能正常渲染标题、列表、代码块
- AI 摘要或问答能返回结果；RAG 问答有引用时可跳转
- 从笔记提取待办，确认后出现在行动看板
- 待办可在队列、进行中、完成之间流转
- 创建提醒，到期后前端通知出现
- 搜索关键词后能跳转到对应笔记
- Docker 演示栈 `.\scripts\demo-up.ps1` 可访问 `http://localhost:8080`

## 常见问题

| 问题 | 处理 |
|------|------|
| 9086 端口冲突 | 停止 IDE 后端或 Docker backend，只保留一个 |
| 数据库连接失败 | 先运行 `.\scripts\dev-up.ps1`，确认 Docker Desktop 已启动 |
| MinIO 图片不可用 | 检查 `NOTO_MINIO_ENABLED=true` 和 9000/9001 端口 |
| AI 不可用 | 检查 `NOTO_AI_ENABLED` 和 `AI_DASHSCOPE_API_KEY`，然后重启后端 |
| 演示数据异常 | 运行 `.\scripts\reset-demo-seed.ps1` 后重启 backend |
