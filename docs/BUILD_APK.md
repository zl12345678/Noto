# 在 Cursor / 终端打包 Android APK

> **日常打包 → [COMMANDS.md](./COMMANDS.md) ④ Android APK**（`.\scripts\build-apk.ps1` 一条命令）  
> 配套：[CAPACITOR.md](./CAPACITOR.md)（Live Reload、真机联调）  
> 应用 ID：`com.noto.zhihui`，原生工程：`frontend/android/`

## 一键打包

```powershell
.\scripts\build-apk.ps1              # 日常 Debug
.\scripts\build-apk.ps1 -SetupSdk    # 首次（无 SDK）
.\scripts\build-apk.ps1 -Install     # 打包并装到手机
```

发版前编辑 `frontend/.env.capacitor` 中的 `VITE_API_BASE_URL`。

---

## 前置条件（备查）

| 项 | 说明 |
|----|------|
| **Node 20+** | 构建 Web 资源 |
| **JDK 21（Android）** | Gradle 编译 APK（Android Studio 自带 JBR；**后端仍为 JDK 17**） |
| **Android SDK** | 含 **Platform-Tools**、**Build-Tools 34+**、**Platform API 35** |
| **环境变量（Windows）** | `ANDROID_HOME` = SDK 根目录，例如 `C:\Users\admin\AppData\Local\Android\Sdk` |
| **后端可访问** | App 内无 Vite 代理，见下文 `.env.capacitor` |

PowerShell 临时设置示例：

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path += ";$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator"
# Android Gradle 用 JBR 21；后端 mvn 仍用 JDK 17
& "D:\Android Studio\jbr\bin\java.exe" -version
adb version
```

---

## 每次发版前：改 API 地址

编辑 **`frontend/.env.capacitor`**（构建时 `--mode capacitor` 会加载）：

```env
# Android 模拟器 → 本机 backend（9086）
VITE_API_BASE_URL=http://10.0.2.2:9086/api/v1

# 真机与电脑同一 WiFi → 改成电脑局域网 IP
# VITE_API_BASE_URL=http://192.168.1.100:9086/api/v1

# 正式 / 测试 HTTPS
# VITE_API_BASE_URL=https://api.example.com/api/v1
```

在 **Cursor 终端**（项目根或 `frontend`）：

```powershell
cd e:\Noto_知微\frontend
npm run build:cap
npx cap sync android
```

等价于 `npm run cap:sync`（若 `package.json` 已配置）。

---

## 二、Debug APK（内测、真机调试）

```powershell
cd e:\Noto_知微\frontend\android
.\gradlew.bat assembleDebug
```

**产物路径：**

```text
frontend\android\app\build\outputs\apk\debug\app-debug.apk
```

安装到已连接手机：

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Debug 常见问题

| 现象 | 处理 |
|------|------|
| 白屏 / 资源 404 | 是否执行过 `build:cap` + `cap sync`；Capacitor 构建 `base` 为 `./` |
| 登录 / 接口失败 | 检查 `.env.capacitor` 的 IP；后端监听 `0.0.0.0:9086`；防火墙放行 |
| **HTTP 明文被拒**（Android 9+） | 开发用 HTTP 时需在原生工程允许 cleartext（见下文「HTTP 明文」） |
| Gradle 下载慢 / 失败 | 工程已配 **华为/阿里云 Maven 镜像**；Android 构建需 **JDK 21**（JBR） |
| `dl.google.com` 超时 | 同上；勿删镜像仓库；仍失败再开 VPN 或代理 |
| **路径含中文**（如 `Noto_知微`） | 已在 `gradle.properties` 设 `android.overridePathCheck=true`；长期建议 clone 到 `E:\Noto` 等纯英文路径 |
| **Gradle 发行包** 下不动 | 改 `gradle/wrapper/gradle-wrapper.properties` 为清华：`https://mirrors.tuna.tsinghua.edu.cn/gradle/gradle-8.11.1-all.zip` |

---

## 三、Release APK（对外分发）

当前 `app/build.gradle` 的 `release` **未配置签名**，需自行增加 keystore。

### 1. 生成 keystore（仅做一次，务必备份密码与文件）

```powershell
keytool -genkeypair -v -keystore noto-release.keystore -alias noto -keyalg RSA -keysize 2048 -validity 10000
```

建议将 `noto-release.keystore` 放在 **`frontend/android/` 外** 的安全目录，**不要提交 Git**。

### 2. 配置签名（本地，勿提交密码）

在 **`frontend/android/gradle.properties`** 末尾增加（或新建 **`frontend/android/keystore.properties`** 并在 `.gitignore` 忽略）：

```properties
# keystore.properties 示例（不要提交仓库）
NOTO_STORE_FILE=E:/secrets/noto-release.keystore
NOTO_STORE_PASSWORD=你的密码
NOTO_KEY_ALIAS=noto
NOTO_KEY_PASSWORD=你的密码
```

在 **`frontend/android/app/build.gradle`** 的 `android {` 块内增加：

```gradle
def keystorePropsFile = rootProject.file("keystore.properties")
def keystoreProps = new Properties()
if (keystorePropsFile.exists()) {
    keystoreProps.load(new FileInputStream(keystorePropsFile))
}

android {
    // ... 原有 defaultConfig、buildTypes ...

    signingConfigs {
        release {
            if (keystorePropsFile.exists()) {
                storeFile file(keystoreProps['NOTO_STORE_FILE'])
                storePassword keystoreProps['NOTO_STORE_PASSWORD']
                keyAlias keystoreProps['NOTO_KEY_ALIAS']
                keyPassword keystoreProps['NOTO_KEY_PASSWORD']
            }
        }
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            if (keystorePropsFile.exists()) {
                signingConfig signingConfigs.release
            }
        }
    }
}
```

（若与现有 `buildTypes { release { ... } }` 重复，请**合并**为一个 `release` 块。）

### 3. 打 Release 包

```powershell
cd e:\Noto_知微\frontend\android
.\gradlew.bat assembleRelease
```

**产物：**

```text
frontend\android\app\build\outputs\apk\release\app-release.apk
```

### 4. 上架 Google Play 用 AAB（可选）

```powershell
.\gradlew.bat bundleRelease
```

产物：`app\build\outputs\bundle\release\app-release.aab`

---

## 四、HTTP 明文（开发环境）

生产请 **HTTPS**。本地 `http://192.168.x.x:9086` 调试时，若出现 **Cleartext HTTP traffic not permitted**：

在 **`frontend/android/app/src/main/AndroidManifest.xml`** 的 `<application>` 上增加：

```xml
android:usesCleartextTraffic="true"
```

或按域名配置 `network_security_config.xml`（更安全，仅放行你的 IP/域名）。

改完需重新 `assembleDebug`。

---

## 五、版本号

在 **`frontend/android/app/build.gradle`**：

```gradle
defaultConfig {
    versionCode 2        // 整数，每次上架 +1
    versionName "0.2.0"  // 展示给用户
}
```

改版本后重新 Gradle 打包即可（无需改 Vue `package.json`，除非你想统一展示）。

---

## 六、手动命令（不用脚本时）

**Debug 全流程：**

```powershell
cd e:\Noto\frontend
npm run build:cap
npx cap sync android
cd android
.\gradlew.bat assembleDebug
Write-Host "APK: $PWD\app\build\outputs\apk\debug\app-debug.apk"
```

**仅重新打 Android（Web 未改）：**

```powershell
cd e:\Noto\frontend\android
.\gradlew.bat assembleDebug
```

> 推荐使用 **`.\scripts\build-apk.ps1`**（见上文「零、一键脚本」）。

## 七、与 IDEA Ultimate / Android Studio 的关系

| 方式 | 说明 |
|------|------|
| **Cursor + gradlew** | 本文档，适合 CI、只要 APK |
| **IDEA Open `frontend/android`** | Run / Debug、Logcat、签名向导 |
| **Android Studio** | 与 IDEA 类似，SDK 向导更直观 |

三种方式 **共用同一套** `frontend/android` 工程。

---

## 八、后端联调检查清单

- [ ] `backend` 已启动，端口 **9086**
- [ ] `application-dev.yml` 或生产配置允许 **CORS**（Capacitor WebView 来源）
- [ ] `.env.capacitor` 中 **VITE_API_BASE_URL** 与设备网络一致
- [ ] 已执行 **build:cap → cap sync → assembleDebug**
- [ ] 真机与电脑 **同一 WiFi**（不要用仅本机 `localhost`）

---

## 九、相关文件

| 文件 | 作用 |
|------|------|
| `frontend/.env.capacitor` | App 内 API 根地址 |
| `frontend/capacitor.config.ts` | appId、webDir、Live Reload |
| `frontend/package.json` | `build:cap`、`cap:sync` |
| `frontend/android/` | Gradle 工程（可 .gitignore，本地 `cap add android` 生成） |

更多 Capacitor 说明见 **[CAPACITOR.md](./CAPACITOR.md)**。