# Capacitor 原生壳（Android / iOS）

前端仍是一套 Vue 代码；Capacitor 把 `frontend/dist` 打进 WebView，生成可安装的 App。

## 环境

| 平台 | 需要 |
|------|------|
| **Android** | [Android Studio](https://developer.android.com/studio)、JDK 17+ |
| **iOS** | macOS + Xcode（仅 mac 可编译 iOS） |
| **共用** | Node 20+、本仓库 `backend` 可被设备访问（局域网 IP 或 HTTPS 测试服） |

## 首次初始化（仓库已含配置时）

```bash
cd frontend
npm install
npm run build:cap
npx cap add android    # 生成 android/ 目录
# npx cap add ios      # 仅 macOS
npx cap sync
```

`android/`、`ios/` 已在 `.gitignore` 中忽略时，每位开发者本地执行 `cap add` 即可。

## 日常命令

```bash
cd frontend
npm run cap:sync       # build:cap + 拷贝 dist 到原生工程
npm run cap:android    # sync 后打开 Android Studio
npm run cap:ios        # sync 后打开 Xcode（mac）
```

## API 地址（重要）

浏览器开发用 Vite 代理 `/api`；**App 内没有代理**，必须配置绝对地址：

编辑 `frontend/.env.capacitor`：

```env
# Android 模拟器访问本机后端
VITE_API_BASE_URL=http://10.0.2.2:9086/api/v1

# 真机 + 电脑同一 WiFi（改成你电脑的 IP）
# VITE_API_BASE_URL=http://192.168.1.100:9086/api/v1

# 生产 / 测试服 HTTPS
# VITE_API_BASE_URL=https://api.example.com/api/v1
```

修改后重新 `npm run cap:sync`。

后端需允许跨域或同域；开发环境可在 Spring 配置 CORS 允许 `capacitor://` / `https://localhost` 等。

## 路由

原生壳内使用 **Hash 路由**（`#/notes`），避免 `file://` / 本地资源路径问题。

## 可选：Live Reload 开发

1. `npm run dev` 启动 Vite（本机 IP:5173）
2. 在 `capacitor.config.ts` 的 `server.url` 填 `http://192.168.x.x:5173`，`cleartext: true`
3. `npx cap sync` 后从 Android Studio 运行

## 与移动 Web 的关系

- **移动 Web / PWA**：浏览器或「添加到主屏幕」，仍走 `npm run build:prod`
- **Capacitor**：`npm run build:cap` + 原生工程，同一套 `MobileLayout` UI

## 后续可加的插件

- `@capacitor/status-bar` 状态栏颜色
- `@capacitor/keyboard` 键盘顶起
- `@capacitor/push-notifications` 推送（需后端配合）