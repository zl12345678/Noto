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

## ④ Android APK

**适用**：打包 Capacitor 手机壳。后端仍用 JDK 17；**Android 构建需要 Java 21**（Android Studio 自带 JBR）。

### 发版前

编辑 `frontend/.env.capacitor` 里的 API 地址（真机用电脑局域网 IP，模拟器用 `10.0.2.2`）。

### 打包

```powershell
# 首次（无 Android SDK）
.\scripts\build-apk.ps1 -SetupSdk

# 日常（Web 构建 + 同步 + Debug APK）
.\scripts\build-apk.ps1

# 装到已连接手机
.\scripts\build-apk.ps1 -Install
```

产物：`frontend\android\app\build\outputs\apk\debug\app-debug.apk`

更多（Release 签名、HTTP 明文等）→ [BUILD_APK.md](./BUILD_APK.md)

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
