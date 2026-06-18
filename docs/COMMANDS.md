# Noto · 知微 — 命令速查

> **在仓库根目录**执行（Windows PowerShell）。  
> 首次克隆后先：`copy .env.example .env`（按需编辑密钥与 AI Key）。

---

## 我该用哪个？

| 你想做什么 | 环境 | 只需记住 |
|------------|------|----------|
| 日常改代码、调试 | **开发** | `dev-up` → IDE 启前后端 |
| 给别人演示全站 | **演示** | `demo-up` → 浏览器 `:8080` |
| 上线 / 云服务器 | **生产** | Linux：`deploy/prod-up.sh` |
| 打包 Android 手机 App | **移动端** | `build-apk.ps1` |
| 查容器 / 端口占用 | 任意 | `stack-status.ps1` |

**原则**：开发用 IDE + Vite；演示/生产用 Docker 全栈。**9086 端口**不能同时被 IDE 后端和 Docker 后端占用。

---

## ① 日常开发（推荐）

**适用**：自己写代码，要热重载、断点调试。

### 开发环境要启动什么？

| 顺序 | 组件 | 运行方式 | 命令 | 端口 | 是否必须 |
|------|------|----------|------|------|----------|
| 0 | **Docker Desktop** | 系统 | 手动打开 Docker Desktop | — | ✅ 必须 |
| 1 | **PostgreSQL**（pgvector） | Docker | `.\scripts\dev-up.ps1` | 5432 | ✅ 必须 |
| 2 | **MinIO**（图片/附件） | Docker | 同上（与 db 一起启动） | 9000 / 9001 | ✅ 必须 |
| 3 | **Spring Boot 后端** | 本机 JDK 17 | `cd backend; .\dev.ps1 -WatchCompile` | 9086 | ✅ 必须 |
| 4 | **Vue 前端** | 本机 Node | `cd frontend; npm run dev` | 5173 | ✅ 必须 |
| — | **通义 AI** | 本机后端 | 见下方「可选」 | — | ❌ 可选 |

> **3 个终端**：① `dev-up`（一次即可） ② 后端 ③ 前端。数据库/MinIO 用 Docker；前后端用 IDE/终端本地跑。

**首次准备**（只做一次）：

```powershell
copy .env.example .env          # 仓库根目录
cd frontend && npm install      # 安装前端依赖
```

### 启动

```powershell
.\scripts\dev-up.ps1          # 1. Docker：PostgreSQL + MinIO

cd backend
.\dev.ps1 -WatchCompile       # 2. 后端（JDK 17）→ :9086

cd ..\frontend
npm install                   # 首次
npm run dev                   # 3. 前端 → :5173
```

也可用 Cursor / VS Code 运行配置 **Backend (DevTools 热部署)** 代替 `dev.ps1`。

### IntelliJ IDEA 运行后端

**① 打开项目（只做一次）**

- **File → Open** → 选 **`E:\Noto\backend\pom.xml`**
- 选 **Open as Project**（Maven 项目）
- 等右下角 Maven 导入完成；**Project SDK 设为 JDK 17**

不要只打开 `E:\Noto` 根目录当普通文件夹，否则读不到 `application.yml`，会报 `DataSource url is not specified`。

**② 先起 Docker**

```powershell
cd E:\Noto
.\scripts\dev-up.ps1
```

**③ 运行后端**

右上角运行配置选 **`ZhihuiApplication (dev)`**（仓库已带 `.run/` 配置），点 Run。

若列表里没有，**Run → Edit Configurations → + → Spring Boot**：

| 项 | 值 |
|----|-----|
| Name | `ZhihuiApplication (dev)` |
| Main class | `com.noto.zhihui.ZhihuiApplication` |
| Active profiles | `dev` |
| Working directory | `E:\Noto\backend` |
| Environment | `NOTO_DEMO_ENABLED=true`（可选） |

**④ 前端**（终端，与 IDEA 无关）

```powershell
cd E:\Noto\frontend
npm run dev
```

**启用 AI**：Edit Configurations → Environment variables 增加  
`NOTO_AI_ENABLED=true` 和 `AI_DASHSCOPE_API_KEY=你的Key`，然后重启 Run。

**DevTools 热重载**：改 Java 后 **Build → Build Project**（Ctrl+F9），约 1–3 秒自动重启。

### 停止

```powershell
# 前后端：终端 Ctrl+C
.\scripts\dev-down.ps1        # 停 Docker（db + minio，保留数据）
```

### 访问

| 地址 | 说明 |
|------|------|
| http://localhost:5173 | 前端（Vite，API 自动代理到 9086） |
| http://localhost:9086/api/v1/health | 后端健康检查 |
| http://localhost:9001 | MinIO 控制台（minioadmin / minioadmin） |

默认账号：`admin` / `admin123`

### 可选

```powershell
# 启用 AI（通义千问）
$env:NOTO_AI_ENABLED = "true"
$env:AI_DASHSCOPE_API_KEY = "你的 Key"
# 然后重启后端

# 重置演示种子数据
.\scripts\reset-demo-seed.ps1   # 之后重启 backend
```

---

## ② Docker 演示

**适用**：不启 IDE，一条命令跑全栈，给别人看。

### 启动

```powershell
.\scripts\demo-up.ps1
```

浏览器打开 **http://localhost:8080**，登录 `admin` / `admin123`。

> 若 9086 已被本地后端占用，脚本会自动释放该端口。

### 停止

```powershell
.\scripts\demo-down.ps1
```

### 变体：IDE 后端 + Docker 数据库

只想用 Docker 跑 db/minio，后端仍用 IDE：

```powershell
.\scripts\demo-up.ps1 -InfraOnly
# 等同 dev-up，然后按「① 日常开发」启 backend + frontend
```

---

## ③ 生产部署

**适用**：正式上线。演示数据必须关闭，JWT 等密钥必须改掉。

### 云服务器（Linux，正式环境）

```bash
cd /path/to/noto
cp deploy/env.prod.example .env && nano .env
./deploy/prod-check.sh --strict
./deploy/prod-up.sh
# 再配 Nginx + HTTPS → 见 deploy/README.md
```

### Windows 本机预演（可选）

```powershell
copy deploy\env.prod.example .env   # 编辑生产密钥
.\scripts\prod-check.ps1 -Strict
.\scripts\prod-up.ps1
```

### 运维

| 操作 | 命令 |
|------|------|
| 停止 | `.\scripts\prod-down.ps1`（Linux：`./deploy/prod-down.sh`） |
| 更新 | `.\scripts\prod-update.ps1`（Linux：`./deploy/prod-update.sh`） |
| 备份数据库 | `.\scripts\prod-backup.ps1` |

---

## ④ 移动 App（UniApp）

**适用**：`mobile/` uni-app 客户端。H5 开发无需 Android SDK；打原生包见 [UNIAPP.md](./UNIAPP.md)。

### 日常开发

```powershell
cd backend; .\dev.ps1 -WatchCompile   # 后端 :9086
cd ..\mobile; npm install; npm run dev:h5   # 移动 H5 :5174
```

### 发版前

编辑 `mobile/.env.production` 里的 `VITE_API_BASE_URL`（真机用电脑局域网 IP，模拟器用 `10.0.2.2`）。

### 构建 App 资源

```powershell
.\scripts\build-apk.ps1          # App-Android 资源
.\scripts\build-apk.ps1 -H5Only  # 仅 H5 静态站
```

安装包需 HBuilderX 云打包 → 详见 [UNIAPP.md](./UNIAPP.md)

---

## ⑤ 偶尔用的维护命令

| 目的 | 命令 |
|------|------|
| 查看 Docker / 端口 | `.\scripts\stack-status.ps1` |
| 重置演示数据 | `.\scripts\reset-demo-seed.ps1` → 重启 backend |
| 导出 OpenAPI | 先启 dev 后端 → `.\scripts\export-openapi.ps1` |

---

## 三种环境对比

| | 开发 | 演示 | 生产 |
|--|------|------|------|
| **入口脚本** | `dev-up.ps1` | `demo-up.ps1` | `deploy/prod-up.sh` |
| **前端** | Vite `:5173` | Nginx `:8080` | Nginx + HTTPS |
| **后端** | IDE `:9086` | Docker `:9086` | Docker（内网） |
| **数据库** | Docker db | Docker db | Docker db（不暴露公网） |
| **演示数据** | 可选 | 默认开 | **必须关** |

---

## 常见问题

| 现象 | 处理 |
|------|------|
| 9086 被占用 | `.\scripts\stack-status.ps1`；演示前 `demo-up` 会自动释放 |
| Gradle / Google 下载失败 | 工程已配国内镜像；Android 用 `build-apk.ps1` |
| 演示数据不对 | `reset-demo-seed.ps1` 后重启 backend |
| pgvector 异常 | `docker compose down db; docker rm -f noto-db; .\scripts\dev-up.ps1` |

---

## 深入阅读

| 文档 | 内容 |
|------|------|
| [DEV_DEPLOY.md](./DEV_DEPLOY.md) | 环境变量、Nginx、AI 配置、故障排查 |
| [BUILD_APK.md](./BUILD_APK.md) | Release 签名、版本号、HTTP 明文 |
| [CAPACITOR.md](./CAPACITOR.md) | Live Reload、真机联调 |
| [deploy/README.md](../deploy/README.md) | 云服务器 Nginx / HTTPS |
| [scripts/README.md](../scripts/README.md) | 全部脚本清单（备查） |
