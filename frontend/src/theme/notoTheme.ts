import { theme } from 'ant-design-vue';
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context';
import type { ResolvedTheme } from '../store/theme';

const { darkAlgorithm, defaultAlgorithm } = theme;

const sharedToken = {
  colorPrimary: '#0891b2',
  colorInfo: '#0ea5e9',
  colorSuccess: '#10b981',
  colorWarning: '#f59e0b',
  colorError: '#ef4444',
  borderRadius: 10,
  borderRadiusLG: 16,
  fontFamily: "'Microsoft YaHei', '微软雅黑', 'PingFang SC', 'Hiragino Sans GB', sans-serif",
  motionEaseInOut: 'cubic-bezier(0.32, 0.72, 0, 1)',
};

const lightToken = {
  ...sharedToken,
  colorText: '#0f172a',
  colorTextSecondary: '#64748b',
  colorBorder: '#e2e8f0',
  colorBorderSecondary: '#e2e8f0',
  colorBgContainer: '#ffffff',
  colorBgLayout: '#f4f7fb',
  boxShadow: '0 2px 8px rgba(15, 23, 42, 0.04), 0 12px 32px rgba(15, 23, 42, 0.06)',
  boxShadowSecondary: '0 1px 3px rgba(15, 23, 42, 0.04)',
};

const darkToken = {
  ...sharedToken,
  colorText: '#e2e8f0',
  colorTextSecondary: '#94a3b8',
  colorBorder: '#334155',
  colorBorderSecondary: '#293548',
  colorBgContainer: '#1a2332',
  colorBgLayout: '#0f1419',
  boxShadow: '0 2px 8px rgba(0, 0, 0, 0.35), 0 12px 32px rgba(0, 0, 0, 0.4)',
  boxShadowSecondary: '0 1px 3px rgba(0, 0, 0, 0.3)',
};

export function buildNotoTheme(resolved: ResolvedTheme): ThemeConfig {
  const isDark = resolved === 'dark';
  return {
    algorithm: isDark ? darkAlgorithm : defaultAlgorithm,
    token: isDark ? darkToken : lightToken,
    components: {
      Layout: {
        headerBg: isDark ? 'rgba(26, 35, 50, 0.92)' : 'rgba(255, 255, 255, 0.92)',
        siderBg: isDark ? '#151c28' : '#ffffff',
        bodyBg: isDark ? '#0f1419' : '#f4f7fb',
      },
      Menu: {
        itemBg: 'transparent',
        itemSelectedBg: isDark ? 'rgba(8, 145, 178, 0.22)' : 'rgba(8, 145, 178, 0.1)',
        itemSelectedColor: isDark ? '#67e8f9' : '#0e7490',
        itemHoverBg: isDark ? 'rgba(255, 255, 255, 0.06)' : 'rgba(15, 23, 42, 0.04)',
        itemActiveBg: isDark ? 'rgba(8, 145, 178, 0.16)' : 'rgba(8, 145, 178, 0.08)',
      },
      Card: {
        borderRadiusLG: 20,
      },
      Button: {
        primaryShadow: isDark
          ? '0 4px 14px rgba(8, 145, 178, 0.35)'
          : '0 4px 14px rgba(8, 145, 178, 0.2)',
        defaultShadow: 'none',
      },
    },
  };
}

/** @deprecated use buildNotoTheme(store.resolved) */
export const notoTheme = buildNotoTheme('light');
