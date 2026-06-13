import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:9086',
        changeOrigin: true,
        timeout: 0,
        proxyTimeout: 0,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return;
          if (
            id.includes('md-editor-v3')
            || id.includes('@codemirror')
            || id.includes('codemirror')
          ) {
            return 'editor';
          }
          if (id.includes('ant-design-vue') || id.includes('@ant-design/icons-vue')) {
            return 'antd';
          }
          if (id.includes('@he-tree')) {
            return 'he-tree';
          }
          if (
            id.includes('vue-router')
            || id.includes('pinia')
            || id.includes('/vue/')
            || id.includes('\\vue\\')
          ) {
            return 'vue-vendor';
          }
          return 'vendor';
        },
      },
    },
  },
});
