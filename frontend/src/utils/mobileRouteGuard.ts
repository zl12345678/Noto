import type { RouteLocationNormalized } from 'vue-router';
import { MOBILE_MAX_WIDTH } from '../composables/useBreakpoint';

export function isMobileViewport(): boolean {
  if (typeof window === 'undefined') return false;
  return window.innerWidth <= MOBILE_MAX_WIDTH;
}

/** 功能与桌面一致，不做路由拦截；保留 API 供其它逻辑判断视口 */
export function resolveMobileRedirect(_to: RouteLocationNormalized): string | null {
  return null;
}