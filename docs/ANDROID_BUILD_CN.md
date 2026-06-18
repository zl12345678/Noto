# 国内环境 Android 打包（已迁移）

> **Capacitor 方案已废弃**，移动端改用 uni-app。见 **[UNIAPP.md](./UNIAPP.md)**。

若仅需手机访问，推荐：

```powershell
cd mobile
npm run dev:h5
```

浏览器打开后「添加到主屏幕」，不依赖 Gradle / Google SDK。

---

# 以下为旧版 Capacitor 文档（归档）

> 2024 起不少 **阿里云/腾讯等「Google 仓库镜像」已下架或 404**，Capacitor/Android 构建 **无法只靠国内镜像** 完成。  
> 可行方案如下（按推荐顺序）。

---

## 方案 A：本机 VPN + Gradle 走代理（推荐）

1. 开启 VPN，确认浏览器能打开 `https://dl.google.com`
2. 编辑 **`frontend/android/gradle.properties`**，取消注释并改成你的代理端口（Clash 常见 **7890**，v2rayN 常见 **10809**）：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

3. **不要**再依赖已 404 的第三方 Google 镜像；工程默认只用 **`google()` + `mavenCentral()`**（走代理访问 Google）。
4. 打包：

```powershell
cd E:\Noto_知微\frontend\android
.\gradlew.bat assembleDebug
```

**IDEA**：Settings → HTTP Proxy → Manual → 同样填 `127.0.0.1:7890`，Gradle 与 IDE 一致。

---

## 方案 B：Gradle 发行包用清华（仅 Gradle 本体，不是 Android SDK）

`gradle-wrapper.properties` 里 **distributionUrl** 可改为（Gradle  zip 往往还能下）：

```properties
distributionUrl=https\://mirrors.tuna.tsinghua.edu.cn/gradle/gradle-8.11.1-all.zip
```

**Android 插件、SDK 仍要从 Google**，仍需方案 A 或 C。

---

## 方案 C：从能翻墙的机器拷贝 Gradle 缓存（离线/半离线）

在 **已成功 build 过一次** 的电脑上，打包缓存目录：

```text
C:\Users\<用户>\.gradle\caches\
C:\Users\<用户>\.gradle\wrapper\dists\
```

拷到本机 **相同路径**（或同一用户目录下），再在本机执行 `assembleDebug`（可能不再大量下载）。

Android **SDK** 若缺失，仍需在 SDK Manager 或能联网的环境安装后，拷贝：

```text
C:\Users\<用户>\AppData\Local\Android\Sdk\
```

---

## 方案 D：可选「华为仓库」（不保证含 AGP 8.7.2）

在 **`gradle.properties`** 设置：

```properties
notoUseHuaweiRepo=true
```

会 **额外** 尝试华为 Maven；**多数情况下仍需要 Google 源**。仅作补充，不能替代 VPN。

---

## 方案 E：路径含中文（`Noto_知微`）

已配置 `android.overridePathCheck=true`。若仍有异常，可用 **虚拟盘符**（不必搬仓库）：

```powershell
subst N: E:\Noto_知微
cd N:\frontend\android
.\gradlew.bat assembleDebug
```

---

## 方案 F：不打包 APK，继续用移动 Web / PWA

浏览器访问 +「添加到主屏幕」，**不依赖 Gradle/Google**，功能与 Capacitor 同一套 Vue。

---

## 常见报错对照

| 报错 | 含义 | 处理 |
|------|------|------|
| `dl.google.com` **Connection timed out** | 未翻墙且未配代理 | 方案 A |
| 镜像 URL **404** | 镜像已停服 | 不要用阿里云 google 镜像；用方案 A/C |
| **non-ASCII path** | 路径含中文 | `overridePathCheck` 或 `subst` |
| **Could not resolve** `gradle:8.7.2` | 拉不到 Android Gradle Plugin | 方案 A + 确认代理对 Gradle 生效 |

---

## 验证代理是否对 Gradle 生效

```powershell
cd E:\Noto_知微\frontend\android
.\gradlew.bat assembleDebug --refresh-dependencies --info 2>&1 | Select-String "dl.google.com|Download|FAILED"
```

应能看到从 `dl.google.com` **成功 Download**，而不是 timeout。

更多命令见 [BUILD_APK.md](./BUILD_APK.md)。