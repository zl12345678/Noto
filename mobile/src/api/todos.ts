import { getAuthToken, request } from '../utils/http';
import { getApiBaseUrl } from '../utils/config';

export type TodoHorizonType = 'action' | 'long_term';

export interface TodoItem {
  id: string;
  workspaceId: string;
  workspaceName?: string | null;
  noteId?: string | null;
  noteTitle?: string | null;
  title: string;
  description?: string | null;
  priority: number;
  status: number;
  horizon: TodoHorizonType;
  dueAt?: string | null;
  completedAt?: string | null;
}

export interface TodoBoard {
  actionTodos: TodoItem[];
  parallelTodos: TodoItem[];
  longTermTodos: TodoItem[];
  longTermActiveCount?: number;
  parallelActiveCount?: number;
  todayCompletedTodos: number;
}

export interface TodoPage {
  records: TodoItem[];
  total: number;
  size?: number;
  current?: number;
}

export interface TodoCreateRequest {
  workspaceId: string;
  noteId?: string | null;
  title: string;
  description?: string;
  priority?: number;
  horizon?: TodoHorizonType;
  dueAt?: string | null;
}

export interface TodoUpdateRequest {
  noteId?: string | null;
  title: string;
  description?: string;
  priority?: number;
  horizon?: TodoHorizonType;
  dueAt?: string | null;
}

export const TODO_HORIZON = {
  ACTION: 'action' as const,
  LONG_TERM: 'long_term' as const,
};

export const TODO_HORIZON_LABEL: Record<TodoHorizonType, string> = {
  action: '近期行动',
  long_term: '长期目标',
};

export const TODO_STATUS = {
  PENDING: 0,
  IN_PROGRESS: 1,
  COMPLETED: 2,
  CANCELLED: 3,
} as const;

export const TODO_STATUS_LABEL: Record<number, string> = {
  0: '待开始',
  1: '进行中',
  2: '已完成',
  3: '已取消',
};

export const TODO_PRIORITY = {
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
} as const;

export const TODO_PRIORITY_LABEL: Record<number, string> = {
  1: '低',
  2: '中',
  3: '高',
};

export function getTodoBoard(workspaceId?: string | null) {
  return request<TodoBoard>({
    url: '/todos/board',
    params: workspaceId ? { workspaceId } : {},
  });
}

export function patchTodoStatus(id: string, status: number) {
  return request<TodoItem>({ url: `/todos/${id}/status`, method: 'PATCH', data: { status } });
}

export function getTodo(id: string) {
  return request<TodoItem>({ url: `/todos/${id}` });
}

export function createTodo(payload: TodoCreateRequest) {
  return request<TodoItem>({ url: '/todos', method: 'POST', data: payload });
}

export function updateTodo(id: string, payload: TodoUpdateRequest) {
  return request<TodoItem>({ url: `/todos/${id}`, method: 'PUT', data: payload });
}

export function deleteTodo(id: string) {
  return request<void>({ url: `/todos/${id}`, method: 'DELETE' });
}

export function listTodos(params: {
  page?: number;
  size?: number;
  workspaceId?: string | null;
  status?: number | null;
  priority?: number | null;
  noteId?: string | null;
  keyword?: string;
  horizon?: TodoHorizonType | null;
}) {
  return request<TodoPage>({ url: '/todos', params });
}

export function isTodoOverdue(item: TodoItem) {
  if (!item.dueAt || item.status === TODO_STATUS.COMPLETED || item.status === TODO_STATUS.CANCELLED) return false;
  return new Date(item.dueAt).getTime() < Date.now();
}

export function isLongTermTodo(item: TodoItem) {
  return item.horizon === TODO_HORIZON.LONG_TERM;
}

export function buildTodosIcsUrl(workspaceId?: string | null) {
  const params = new URLSearchParams();
  if (workspaceId) params.set('workspaceId', workspaceId);
  const query = params.toString();
  return `${getApiBaseUrl()}/todos/export/ics${query ? `?${query}` : ''}`;
}

export async function exportTodosIcs(workspaceId?: string | null) {
  const url = buildTodosIcsUrl(workspaceId);

  // #ifdef H5
  const response = await fetch(url, {
    headers: getAuthToken() ? { Authorization: `Bearer ${getAuthToken()}` } : {},
  });
  if (!response.ok) throw new Error('导出 ICS 失败');
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = 'noto-todos.ics';
  anchor.click();
  URL.revokeObjectURL(objectUrl);
  return;
  // #endif

  // #ifndef H5
  return new Promise<void>((resolve, reject) => {
    uni.downloadFile({
      url,
      header: getAuthToken() ? { Authorization: `Bearer ${getAuthToken()}` } : {},
      success: (res) => {
        if ((res.statusCode || 0) < 200 || (res.statusCode || 0) >= 300) {
          reject(new Error('导出 ICS 失败'));
          return;
        }
        uni.openDocument({
          filePath: res.tempFilePath,
          showMenu: true,
          success: () => resolve(),
          fail: () => reject(new Error('ICS 已下载，但当前设备无法直接打开，请在文件管理器中选择日历应用导入')),
        });
      },
      fail: () => reject(new Error('导出 ICS 失败')),
    });
  });
  // #endif
}
