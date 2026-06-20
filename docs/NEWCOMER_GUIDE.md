# Noto 新人开发、调试与部署指南

这份文档给刚拉代码的同学使用。目标是先把项目跑起来，再知道去哪里改代码、如何调试、如何演示和部署。

更完整的命令索引见 [COMMANDS.md](./COMMANDS.md)，生产部署细节见 [DEV_DEPLOY.md](./DEV_DEPLOY.md) 和 [deploy/README.md](../deploy/README.md)。

---

## 1. 先认识项目

Noto 是一个知识管理和 AI 辅助应用，采用前后端分离架构。

| 目录 | 作用 |
|------|------|
| `backend/` | Spring Boot 后端，提供 REST API、JWT 鉴权、AI、搜索、附件、演示数据 |
| `frontend/` | Vue 3 Web 前端，日常桌面端开发主要在这里 |
| `mobile/` | uni-app 移动端，H5 和 App 资源构建在这里 |
| `docs/` | 开发、部署、移动端、OpenAPI 等文档 |
| `scripts/` | Windows PowerShell 脚本，用于开发、演示、生产预演、APK 构建 |
| `deploy/` | Linux 生产部署脚本、Nginx 配置、生产 compose 覆盖文件 |
| `docker-compose.yml` | 开发基础设施和 Docker 演示栈共用的 compose 文件 |

本地开发时推荐这样理解链路：

```text
浏览器 http://localhost:5173
  -> frontend Vite dev server
  -> /api 代理到 http://localhost:9086
  -> backend Spring Boot
  -> Docker PostgreSQL + MinIO
```

Docker 演示和生产时：

```text
浏览器 http://localhost:8080
  -> frontend Nginx 容器
  -> backend 容器
  -> db / minio 容器
```

---

## 2. 首次准备

### 2.1 必装环境

| 工具 | 建议版本 | 用途 |
|------|----------|------|
| JDK | 17 | 后端运行和调试 |
| Node.js | 18+，推荐 20 | Web 前端、移动端依赖 |
| Docker Desktop | Compose v2 | PostgreSQL、MinIO、Docker 演示栈 |
| IntelliJ IDEA 或 VS Code/Cursor | 任选 | 后端调试、前端开发 |

后端项目自带 Maven Wrapper，通常不需要单独安装 Maven。

Windows 上如果命令行直接运行 `backend/mvnw.cmd`，需要设置 `JAVA_HOME`。例如：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
```

`backend/dev.ps1` 会自动查找常见 JDK 17 路径，因此日常启动后端优先用 `.\dev.ps1`。

### 2.2 复制环境变量

在仓库根目录执行：

```powershell
copy .env.example .env
```

本地开发可以先使用默认值。准备演示或生产时再修改 `.env`。

重要变量：

| 变量 | 本地默认 | 说明 |
|------|----------|------|
| `POSTGRES_DB` | `noto_zhihui_dev` | 数据库名 |
| `POSTGRES_PASSWORD` | `123456` | 本地数据库密码 |
| `NOTO_DEMO_ENABLED` | `true` | 是否写入演示账号和演示数据 |
| `NOTO_AI_ENABLED` | `false` | 是否启用通义千问 AI |
| `AI_DASHSCOPE_API_KEY` | 空 | AI Key，不能提交到仓库 |
| `NOTO_MINIO_ENABLED` | `true` | 是否启用附件和图片上传 |

---

## 3. 本地开发启动

新人推荐使用 3 个终端。

### 终端 1：启动基础设施

在仓库根目录：

```powershell
.\scripts\dev-up.ps1
```

它会启动：

| 服务 | 地址 |
|------|------|
| PostgreSQL pgvector | `localhost:5432` |
| MinIO API | `http://localhost:9000` |
| MinIO 控制台 | `http://localhost:9001` |

MinIO 默认账号：`minioadmin / minioadmin`。

### 终端 2：启动后端

```powershell
cd backend
.\dev.ps1 -WatchCompile
```

后端地址：

| 地址 | 说明 |
|------|------|
| `http://localhost:9086/api/v1/health` | 健康检查 |
| `http://localhost:9086/swagger-ui.html` | Swagger UI，dev 环境可用 |
| `http://localhost:9086/v3/api-docs.yaml` | OpenAPI YAML |

`-WatchCompile` 会监听 Java 文件变化并自动编译，配合 Spring DevTools 触发热重载。

常用参数：

```powershell
.\dev.ps1                  # 普通启动
.\dev.ps1 -WatchCompile    # 推荐，自动编译热重载
.\dev.ps1 -Debug           # 打开 JDWP 调试端口 5005
.\dev.ps1 -NoDemo          # 不写入演示数据
```

默认账号：`admin / admin123`。

### 终端 3：启动 Web 前端

```powershell
cd frontend
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

前端 `/api` 请求会通过 [frontend/vite.config.ts](../frontend/vite.config.ts) 代理到后端 `http://localhost:9086`。

---

## 4. IDE 调试

### 4.1 IntelliJ IDEA 后端调试

推荐用 IDEA 打开后端 Maven 项目：

1. `File -> Open`
2. 选择 `backend/pom.xml`
3. 选择 `Open as Project`
4. Project SDK 设置为 JDK 17
5. 先在仓库根目录运行 `.\scripts\dev-up.ps1`
6. 运行 `ZhihuiApplication`

如果要断点调试，有两种方式：

| 方式 | 用法 |
|------|------|
| IDEA 直接 Debug `ZhihuiApplication` | 最简单，适合日常断点 |
| 命令行 `.\dev.ps1 -Debug` | IDEA 通过 Remote JVM Debug 连接 `localhost:5005` |

运行配置建议：

| 项 | 值 |
|----|-----|
| Main class | `com.noto.zhihui.ZhihuiApplication` |
| Active profiles | `dev` |
| Working directory | `backend` |
| Environment | `NOTO_DEMO_ENABLED=true` |

### 4.2 前端调试

Web 前端入口：

| 文件 | 作用 |
|------|------|
| `frontend/src/main.ts` | Vue 应用入口 |
| `frontend/src/router/index.ts` | 路由表 |
| `frontend/src/api/http.ts` | Axios 实例、JWT、错误处理 |
| `frontend/src/store/` | Pinia 状态 |
| `frontend/src/views/` | 页面 |
| `frontend/src/components/` | 组件 |

浏览器 DevTools 里看网络请求即可。接口路径基本都在 `frontend/src/api/*.ts`。

### 4.3 后端代码入口

| 目录 | 作用 |
|------|------|
| `backend/src/main/java/com/noto/zhihui/controller` | REST API |
| `backend/src/main/java/com/noto/zhihui/service` | 业务接口 |
| `backend/src/main/java/com/noto/zhihui/service/impl` | 业务实现 |
| `backend/src/main/java/com/noto/zhihui/mapper` | MyBatis-Plus Mapper |
| `backend/src/main/java/com/noto/zhihui/entity` | 数据库实体 |
| `backend/src/main/java/com/noto/zhihui/dto` | 请求对象 |
| `backend/src/main/java/com/noto/zhihui/vo` | 返回对象 |
| `backend/src/main/resources/application-dev.yml` | 开发环境配置 |
| `backend/src/main/resources/application-prod.yml` | 生产环境配置 |

---

## 5. 常用开发任务

### 5.1 后端编译

```powershell
cd backend
.\mvnw.cmd -q -DskipTests compile
```

### 5.2 后端测试

```powershell
cd backend
.\mvnw.cmd test
```

部分集成测试依赖 Docker 和 PostgreSQL Testcontainers，运行前确保 Docker Desktop 已启动。

### 5.3 前端构建

生产静态资源构建：

```powershell
cd frontend
npm run build:prod
```

严格类型检查加构建：

```powershell
cd frontend
npm run build
```

说明：Docker 镜像构建使用 `build:prod`。`npm run build` 会先执行 `vue-tsc -b`，适合作为类型债务收敛目标；如果当前分支存在既有 TypeScript 错误，它可能失败，但不影响使用 `build:prod` 生成前端静态资源。

### 5.4 移动端 H5 开发

先启动后端和基础设施，然后：

```powershell
cd mobile
npm install
npm run dev:h5
```

移动端页面配置在 [mobile/src/pages.json](../mobile/src/pages.json)。

### 5.5 导出 OpenAPI

先启动后端，然后在仓库根目录：

```powershell
.\scripts\export-openapi.ps1
```

输出文件：

```text
docs/openapi.yaml
```

---

## 6. AI 和演示数据

### 6.1 启用 AI

不要把 Key 写进 Git。推荐在当前 PowerShell 会话设置：

```powershell
$env:NOTO_AI_ENABLED = "true"
$env:AI_DASHSCOPE_API_KEY = "你的百炼或通义 API Key"
cd backend
.\dev.ps1 -WatchCompile
```

验证：

```text
GET http://localhost:9086/api/v1/ai/status
```

看到 `enabled: true` 说明已启用。

### 6.2 重置演示数据

如果页面数据不对，或想恢复演示数据：

```powershell
.\scripts\reset-demo-seed.ps1
```

然后重启后端。

演示账号：

```text
admin / admin123
```

---

## 7. Docker 演示

当你想一条命令跑完整站点给别人看，不需要本机 IDE 后端和 Vite 前端。

在仓库根目录：

```powershell
.\scripts\demo-up.ps1
```

访问：

```text
http://localhost:8080
```

停止：

```powershell
.\scripts\demo-down.ps1
```

如果你只想启动 db 和 minio，后端仍用 IDE 跑：

```powershell
.\scripts\demo-up.ps1 -InfraOnly
```

注意：`demo-up.ps1` 会使用 Docker 后端，占用 `9086`。不要和本机 IDE 后端同时跑。

---

## 8. 生产部署

生产部署推荐 Linux 云服务器，使用 `deploy/` 目录脚本。

### 8.1 服务器前置要求

| 项 | 要求 |
|----|------|
| 系统 | Ubuntu / Debian |
| 运行环境 | Docker、Docker Compose、Nginx、Certbot |
| 安全组 | 只开放 22、80、443 |
| 域名 | 已解析到服务器 |

不要把 PostgreSQL、MinIO、后端 API 端口直接暴露到公网。

### 8.2 部署流程

```bash
cd /opt/noto
cp deploy/env.prod.example .env
nano .env
chmod +x deploy/*.sh
./deploy/prod-check.sh --strict
./deploy/prod-up.sh
```

然后配置宿主机 Nginx 和 HTTPS：

```bash
sudo sed 's/noto.example.com/你的域名/g' deploy/nginx/noto.conf \
  | sudo tee /etc/nginx/sites-available/noto
sudo ln -sf /etc/nginx/sites-available/noto /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
sudo certbot --nginx -d 你的域名
```

生产 `.env` 必改：

| 变量 | 要求 |
|------|------|
| `POSTGRES_PASSWORD` | 强密码 |
| `NOTO_JWT_SECRET` | 至少 32 位随机字符串 |
| `MINIO_ROOT_PASSWORD` | 强密码 |
| `NOTO_DEMO_ENABLED` | 必须为 `false` |
| `AI_DASHSCOPE_API_KEY` | 按需配置 |

上线后验证：

```text
https://你的域名/api/v1/health
```

### 8.3 生产运维命令

```bash
docker compose logs -f backend
./deploy/prod-update.sh
./deploy/prod-backup.sh /var/backups/noto
./deploy/prod-down.sh
```

Windows 本机生产预演可用：

```powershell
copy deploy\env.prod.example .env
.\scripts\prod-check.ps1 -Strict
.\scripts\prod-up.ps1
```

---

## 9. 常见问题

### 9.1 端口被占用

查看状态：

```powershell
.\scripts\stack-status.ps1
```

重点端口：

| 端口 | 服务 |
|------|------|
| 5173 | Web 前端 dev server |
| 8080 | Docker 前端 |
| 9086 | 后端 API |
| 5432 | PostgreSQL |
| 9000 / 9001 | MinIO |

`9086` 只能有一个后端占用。本机后端和 Docker 后端不要同时启动。

### 9.2 后端报数据库连接失败

先确认基础设施已启动：

```powershell
.\scripts\dev-up.ps1
.\scripts\stack-status.ps1
```

再确认 `.env` 和 `application-dev.yml` 中数据库名、用户名、密码一致。

### 9.3 前端请求一直失败

检查：

1. 后端是否能访问 `http://localhost:9086/api/v1/health`
2. 前端是否通过 `npm run dev` 启动在 `http://localhost:5173`
3. `frontend/vite.config.ts` 中 `/api` 是否代理到 `http://localhost:9086`
4. 浏览器 Network 里响应是否为 401，401 通常是登录态过期

### 9.4 上传图片失败

检查：

1. MinIO 是否启动：`http://localhost:9001`
2. 后端 dev 配置是否启用 `noto.minio.enabled=true`
3. 当前笔记是否已保存，上传需要有效 `noteId`

### 9.5 Docker 演示数据不对

```powershell
.\scripts\reset-demo-seed.ps1
.\scripts\demo-down.ps1
.\scripts\demo-up.ps1
```

如果需要完全清空卷，先确认不需要保留数据，再查看 `demo-down.ps1` 的 `-RemoveVolumes` 参数。

---

## 10. 新人改代码建议

先按下面顺序熟悉：

1. 跑通 `dev-up`、后端、前端，能登录 `admin / admin123`
2. 看 [frontend/src/router/index.ts](../frontend/src/router/index.ts)，知道页面入口
3. 看 `frontend/src/api/*.ts`，知道前端如何调用后端
4. 找对应后端 `controller -> service -> mapper -> entity`
5. 改一个小页面或小接口，跑一次构建和测试
6. 需要 AI 时再配置 Key，不要一开始就把 AI 作为启动前置条件

推荐每次提交前至少执行：

```powershell
cd backend
.\mvnw.cmd -q -DskipTests compile

cd ..\frontend
npm run build:prod
```

如果改了后端核心流程，再跑后端测试：

```powershell
cd backend
.\mvnw.cmd test
```

如果改了类型定义、API 封装或组件 props，再额外跑严格类型检查：

```powershell
cd frontend
npm run build
```
