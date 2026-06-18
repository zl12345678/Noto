# Noto · 知微 — UniApp 移动客户端

> 替代原 Capacitor WebView 壳。移动端使用独立 **uni-app** 工程（`mobile/`），与 Web 桌面端（`frontend/`）共用同一套 Spring Boot API。

---

## 工程结构

```text
mobile/
├── src/
│   ├── pages/          # 首页、笔记、待办、我的、登录、搜索
│   ├── api/            # REST 封装（对接 /api/v1）
│   ├── stores/         # 登录态
│   └── utils/http.ts   # uni.request + JWT
├── .env.development    # H5 开发：/api/v1（Vite 代理到 :9086）
└── .env.production     # 发版前改真实后端地址
```

Web 桌面端继续负责深度写作、AI 编排；移动端聚焦 **今日行动、待办、读笔记、搜索**。

---

## 日常开发

```powershell
# 1. 启动后端（与 Web 相同）
cd backend; .\dev.ps1 -WatchCompile

# 2. 启动 uni-app H5
cd ..\mobile
npm install
npm run dev:h5
```

浏览器打开 **http://localhost:5174**，默认账号 `admin` / `admin123`。

---

## API 地址

| 场景 | 配置 |
|------|------|
| H5 本地开发 | `.env.development` → `VITE_API_BASE_URL=/api/v1`（Vite 代理） |
| 真机调试 H5 | 改为 `http://192.168.x.x:9086/api/v1` |
| Android App | `.env.production` 中填局域网或生产 API |
| 模拟器访问本机 | `http://10.0.2.2:9086/api/v1` |

---

## 打包 Android App

uni-app 原生包需 **HBuilderX** 云打包或离线 SDK：

```powershell
cd mobile
npm install
npm run build:app-android
```

然后在 [HBuilderX](https://www.dcloud.io/hbuilderx.html) 中打开 `mobile` 目录 → 发行 → 原生 App-云打包。

也可使用仓库脚本（构建 App 资源）：

```powershell
.\scripts\build-apk.ps1
```

> 原 `frontend/android` Capacitor 工程已废弃，不再维护。

---

## 与 Web 移动版的区别

| | Web 移动版 (`frontend`) | UniApp (`mobile`) |
|--|-------------------------|-------------------|
| 技术 | Vue 3 + Ant Design Vue | uni-app + 原生组件 |
| 场景 | 浏览器 / PWA | App、小程序（可扩展） |
| 功能 | 与桌面同路由全集 | 移动 MVP 子集 |
| 打包 | `npm run build` | `npm run build:app-android` |

---

## 移动端设计原则

与 Web 桌面 **共用 API**，但交互按手机场景裁剪：

| 定位 | 移动端 | 桌面 Web |
|------|--------|----------|
| 首页 | 今日行动摘要（各 3 条）+ 大按钮改状态 | 完整看板拖拽 |
| 待办 | 分段：队列 / 进行中 / 长期 | 多列看板 |
| 笔记 | 阅读优先，底部栏编辑 | 完整 MD 编辑器 |
| AI | Tab 直达，快捷问题 + 对话气泡 | 流式 SSE + 智能体 |
| 我的 | 搜索、提醒、网盘、分享、设置 | 全模块 + Cmd+K |

## 底部 Tab（5 个）

**行动 · 笔记 · AI · 待办 · 我的**

## 页面清单

- 登录 / **注册**
- 首页：今日行动看板 + **快捷入口**（AI、搜索、提醒、网盘）
- 笔记：**知识库 Hub** → 列表 → **阅读/编辑/收藏/新建**
- 待办：看板列表 + **新建待办**
- **AI 助手**：知识库问答（非流式）
- **提醒**：列表、取消、删除、跳转关联笔记
- **网盘**：按知识库浏览文件
- **我的分享**：列表、复制链接、撤销
- 我的：模块入口 + **账号与 AI 设置**
- 搜索：关键字检索跳转文档

后续可按需扩展：AI 流式 SSE、文档树、拖拽看板、智能体办事等。
