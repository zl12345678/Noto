import type { DashboardStats } from '../api/dashboard';

/** 系统自动生成的知识库首页 */
export function isAutoHomepageNote(note: { title?: string; summary?: string | null }) {
  return note.title === '首页' && note.summary === '知识库首页';
}

export const DEMO_ACCOUNT_USERNAME = 'admin';

export function isDemoAccount(user?: { username?: string } | null): boolean {
  return user?.username === DEMO_ACCOUNT_USERNAME;
}

export const ONBOARDING_DISMISS_KEY = 'noto-onboarding-dismissed';

function resolveDismissKey(userId?: string | number | null): string {
  if (userId != null && userId !== '') {
    return `${ONBOARDING_DISMISS_KEY}:${userId}`;
  }
  return ONBOARDING_DISMISS_KEY;
}

export function isOnboardingDismissed(userId?: string | number | null): boolean {
  return localStorage.getItem(resolveDismissKey(userId)) === '1';
}

export function dismissOnboarding(userId?: string | number | null): void {
  localStorage.setItem(resolveDismissKey(userId), '1');
}

export function resetOnboardingDismiss(userId?: string | number | null): void {
  localStorage.removeItem(resolveDismissKey(userId));
}

/** 后端未返回 onboardingEligible 时的兜底判断 */
export function inferOnboardingEligible(stats: DashboardStats): boolean {
  if (stats.pendingTodos > 0) return false;
  if ((stats.actionTodos?.length ?? 0) > 0) return false;
  if ((stats.parallelTodos?.length ?? 0) > 0) return false;

  const recent = stats.recentNotes ?? [];
  if (recent.some((n) => !isAutoHomepageNote(n))) return false;
  if (stats.totalNotes === 0) return true;
  if (recent.length > 0 && recent.every(isAutoHomepageNote)) return true;
  return stats.totalNotes === 1 && recent.length === 0;
}
