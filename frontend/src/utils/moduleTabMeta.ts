import type { RouteLocationNormalized } from 'vue-router';

export type ModuleTabIcon =
  | 'home'
  | 'search'
  | 'ai'
  | 'todo'
  | 'reminder'
  | 'drive'
  | 'share'
  | 'note'
  | 'profile'
  | 'default';

export interface ModuleTabMeta {
  id: string;
  label: string;
  icon: ModuleTabIcon;
  closable: boolean;
}

const SKIP_TAB_ROUTES = new Set(['profile']);

export function resolveModuleTabMeta(
  route: RouteLocationNormalized,
  workspaceName?: string,
): ModuleTabMeta | null {
  const name = typeof route.name === 'string' ? route.name : '';
  if (!name || SKIP_TAB_ROUTES.has(name)) return null;

  if (name === 'dashboard') {
    return { id: 'dashboard', label: '首页', icon: 'home', closable: false };
  }
  if (name === 'ai') {
    return { id: 'ai', label: 'AI 助手', icon: 'ai', closable: true };
  }
  if (name === 'search') {
    const q = typeof route.query.q === 'string' ? route.query.q.trim() : '';
    return {
      id: 'search',
      label: q ? `搜索 · ${truncate(q, 14)}` : '搜索',
      icon: 'search',
      closable: true,
    };
  }
  if (name === 'todos') {
    const view = typeof route.query.view === 'string' ? route.query.view : 'board';
    const viewLabel = view === 'all' ? '全部' : view === 'action' ? '行动' : '看板';
    return { id: 'todos', label: `待办 · ${viewLabel}`, icon: 'todo', closable: true };
  }
  if (name === 'reminders') {
    return { id: 'reminders', label: '提醒', icon: 'reminder', closable: true };
  }
  if (name === 'drive') {
    return { id: 'drive', label: '网盘', icon: 'drive', closable: true };
  }
  if (name === 'my-shares') {
    return { id: 'my-shares', label: '我的分享', icon: 'share', closable: true };
  }
  if (name === 'notes') {
    const workspaceId = typeof route.query.workspace === 'string' ? route.query.workspace : '';
    const wsLabel = workspaceName ? `文档 · ${truncate(workspaceName, 10)}` : '文档';
    return {
      id: resolveNotesTabId(workspaceId),
      label: wsLabel,
      icon: 'note',
      closable: true,
    };
  }

  return {
    id: `route:${route.fullPath}`,
    label: '页面',
    icon: 'default',
    closable: true,
  };
}

function truncate(text: string, max: number) {
  if (text.length <= max) return text;
  return `${text.slice(0, max)}…`;
}

export function truncateTabLabel(text: string, max = 18) {
  return truncate(text.trim() || '未命名', max);
}

/** 同一知识库的列表与详情共用一个模块标签 */
export function resolveNotesTabId(workspaceId?: string | null) {
  const id = workspaceId?.trim();
  return id ? `notes:${id}` : 'notes';
}

export function buildNotesTabLabel(workspaceName?: string | null, noteTitle?: string | null) {
  if (noteTitle?.trim()) {
    return truncateTabLabel(noteTitle);
  }
  if (workspaceName?.trim()) {
    return `文档 · ${truncate(workspaceName.trim(), 10)}`;
  }
  return '文档';
}
