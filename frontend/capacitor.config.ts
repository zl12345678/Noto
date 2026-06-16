import type { CapacitorConfig } from '@capacitor/cli';

/**
 * 原生壳加载 frontend/dist（需先 npm run build:cap）
 * API：在 .env.capacitor 中设置 VITE_API_BASE_URL（真机/模拟器不能走 Vite proxy）
 */
const config: CapacitorConfig = {
  appId: 'com.noto.zhihui',
  appName: 'Noto 知微',
  webDir: 'dist',
  server: {
    /** WebView 内本地资源用 https scheme，避免混合内容问题 */
    androidScheme: 'https',
    /** 开发时可取消注释，让壳直接连本机 Vite（需同 WiFi / adb reverse） */
    // url: 'http://192.168.x.x:5173',
    // cleartext: true,
  },
  android: {
    allowMixedContent: true,
  },
};

export default config;