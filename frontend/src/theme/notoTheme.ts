import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context';

export const notoTheme: ThemeConfig = {
  token: {
    colorPrimary: '#0891b2',
    colorInfo: '#0ea5e9',
    colorSuccess: '#10b981',
    colorWarning: '#f59e0b',
    colorError: '#ef4444',
    colorText: '#0f172a',
    colorTextSecondary: '#64748b',
    colorBorder: '#e2e8f0',
    colorBorderSecondary: '#e2e8f0',
    colorBgContainer: '#ffffff',
    colorBgLayout: '#f4f7fb',
    borderRadius: 10,
    borderRadiusLG: 16,
    fontFamily: "'Microsoft YaHei', '微软雅黑', 'PingFang SC', 'Hiragino Sans GB', sans-serif",
    boxShadow: '0 2px 8px rgba(15, 23, 42, 0.04), 0 12px 32px rgba(15, 23, 42, 0.06)',
    boxShadowSecondary: '0 1px 3px rgba(15, 23, 42, 0.04)',
    motionEaseInOut: 'cubic-bezier(0.32, 0.72, 0, 1)',
  },
  components: {
    Layout: {
      headerBg: 'rgba(255, 255, 255, 0.92)',
      siderBg: '#ffffff',
      bodyBg: '#f4f7fb',
    },
    Menu: {
      itemBg: 'transparent',
      itemSelectedBg: 'rgba(8, 145, 178, 0.1)',
      itemSelectedColor: '#0e7490',
      itemHoverBg: 'rgba(15, 23, 42, 0.04)',
      itemActiveBg: 'rgba(8, 145, 178, 0.08)',
    },
    Card: {
      borderRadiusLG: 20,
    },
    Button: {
      primaryShadow: '0 4px 14px rgba(8, 145, 178, 0.2)',
      defaultShadow: 'none',
    },
  },
};
