# Noto 移动端说明

> 移动端当前使用 uni-app 工程 `mobile/`，原 Capacitor 方案已废弃。

## 定位

Web 桌面端负责深度写作、AI 编排和完整管理；移动端聚焦：

- 今日行动
- 待办处理
- 阅读笔记
- 搜索
- 轻量 AI/网盘入口

## 工程结构

```text
mobile/
├── src/
│   ├── pages/          # 首页、笔记、待办、我的、登录、搜索
│   ├── api/            # REST 封装，对接 /api/v1
│   ├── stores/         # 登录态
│   └── utils/http.ts   # uni.request + JWT
├── .env.development    # H5 开发：/api/v1，Vite 代理到 :9086
└── .env.production     # 发版前改真实后端地址
```

## H5 开发

先启动后端和基础设施，见 [DEVELOPMENT.md](./DEVELOPMENT.md)。

```powershell
cd mobile
npm install
npm run dev:h5
```

默认访问端口以终端输出为准，通常是 `5174`。

## App 资源构建

```powershell
.\scripts\build-apk.ps1
```

只构建 H5 静态资源：

```powershell
.\scripts\build-apk.ps1 -H5Only
```

安装包需使用 HBuilderX 云打包。发版前修改 `mobile/.env.production`：

```env
VITE_API_BASE_URL=https://你的域名/api/v1
```

真机调试如果访问本机后端，使用电脑局域网 IP；Android 模拟器可用 `10.0.2.2`。

## 移动 Web 验收

用 Chrome 设备模拟 `<768px` 或真机检查：

- 底 Tab：首页 / 笔记 / 待办 / 我的。
- 顶栏更多：AI 助手、网盘、我的分享、提醒、首页。
- 首页今日行动看板可横向滑动，状态变更与桌面一致。
- 笔记阅读模式 Markdown 渲染正常。
- 编辑模式、AI 侧栏、附件、分享、提取待办可打开。
- 待办编辑入口收纳在“更多”中。
- AI 助手输入框底部无异常留白。
- 网盘、分享、提醒页面可进入并返回。
- 登录过期能跳回登录页。

## 废弃说明

Capacitor / Gradle 原生壳已移除，不再维护旧 Android 原生壳流程。移动端统一看本文。
