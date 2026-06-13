import { defineStore } from 'pinia';
import { getCurrentUser, login as loginApi, logout as logoutApi, register as registerApi } from '../api/auth';
import type { CurrentUser, LoginRequest } from '../types/auth';

const TOKEN_KEY = 'noto-zhihui-token';
const REMEMBER_KEY = 'noto-zhihui-remember';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || '',
    currentUser: null as CurrentUser | null,
    rememberMe: localStorage.getItem(REMEMBER_KEY) === 'true',
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
  },
  actions: {
    restore() {
      this.token = localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || '';
      this.rememberMe = localStorage.getItem(REMEMBER_KEY) === 'true';
    },
    setRememberMe(remember: boolean) {
      this.rememberMe = remember;
      localStorage.setItem(REMEMBER_KEY, String(remember));
    },
    async login(payload: LoginRequest & { rememberMe?: boolean }) {
      try {
        const data = await loginApi(payload) as any;
        if (!data?.token || !data.user) {
          throw new Error('登录成功但未返回登录信息');
        }
        this.token = data.token;
        this.currentUser = data.user;
        if (payload.rememberMe) {
          localStorage.setItem(TOKEN_KEY, this.token);
          this.setRememberMe(true);
        } else {
          sessionStorage.setItem(TOKEN_KEY, this.token);
          this.setRememberMe(false);
        }
        const { useWorkspaceStore } = await import('./workspace');
        await useWorkspaceStore().refresh();
      } catch (error: any) {
        const message = error?.response?.data?.message || error?.message || '登录失败';
        throw new Error(message);
      }
    },
    async register(payload: { username: string; email: string; nickname: string; password: string }) {
      try {
        const data = await registerApi(payload) as any;
        if (!data?.token || !data.user) {
          throw new Error('注册成功但未返回登录信息');
        }
        this.token = data.token;
        this.currentUser = data.user;
        localStorage.setItem(TOKEN_KEY, this.token);
        this.setRememberMe(true);
        const { useWorkspaceStore } = await import('./workspace');
        await useWorkspaceStore().refresh();
      } catch (error: any) {
        const message = error?.response?.data?.message || error?.message || '注册失败';
        throw new Error(message);
      }
    },
    async fetchCurrentUser() {
      const response = await getCurrentUser() as any;
      this.currentUser = response;
    },
    async logout() {
      try {
        await logoutApi();
      } finally {
        this.token = '';
        this.currentUser = null;
        localStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(REMEMBER_KEY);
        this.rememberMe = false;
        const { useAiChatStore } = await import('./aiChat');
        useAiChatStore().clearSession();
        const { useWorkspaceStore } = await import('./workspace');
        useWorkspaceStore().reset();
        const { useAiPrefsStore } = await import('./aiPrefs');
        useAiPrefsStore().reset();
        const { useModuleTabsStore } = await import('./moduleTabs');
        useModuleTabsStore().reset();
        localStorage.removeItem('noto-module-tab-pins');
      }
    },
  },
});
