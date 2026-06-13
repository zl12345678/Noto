import http from './http';

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
  createdAt?: string;
  updatedAt?: string;
}

export interface TodoBoard {
  actionTodos: TodoItem[];
  parallelTodos: TodoItem[];
  longTermTodos: TodoItem[];
  longTermActiveCount: number;
  parallelActiveCount: number;
  todayCompletedTodos: number;
}

export interface TodoPage {
  records: TodoItem[];
  total: number;
  size: number;
  current: number;
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

/** 待办类型（近期可执行 vs 长期目标），与「待开始/进行中」状态无关 */
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
  return http.get<TodoPage>('/todos', { params });
}

export function getTodoBoard(workspaceId?: string | null) {
  return http.get<TodoBoard>('/todos/board', {
    params: workspaceId ? { workspaceId } : {},
  });
}

export function getTodo(id: string) {
  return http.get<TodoItem>(`/todos/${id}`);
}

export function createTodo(payload: TodoCreateRequest) {
  return http.post<TodoItem>('/todos', payload);
}

export function updateTodo(id: string, payload: TodoUpdateRequest) {
  return http.put<TodoItem>(`/todos/${id}`, payload);
}

export function patchTodoStatus(id: string, status: number) {
  return http.patch<TodoItem>(`/todos/${id}/status`, { status });
}

export function deleteTodo(id: string) {
  return http.delete<void>(`/todos/${id}`);
}

export function isTodoOverdue(item: TodoItem) {
  if (!item.dueAt || item.status === TODO_STATUS.COMPLETED || item.status === TODO_STATUS.CANCELLED) {
    return false;
  }
  return new Date(item.dueAt).getTime() < Date.now();
}

export function isLongTermTodo(item: TodoItem) {
  return item.horizon === TODO_HORIZON.LONG_TERM;
}

export async function downloadTodosIcs(workspaceId?: string | null) {
  const token =
    localStorage.getItem('noto-zhihui-token') || sessionStorage.getItem('noto-zhihui-token');
  const params = workspaceId ? `?workspaceId=${encodeURIComponent(workspaceId)}` : '';
  const response = await fetch(`/api/v1/todos/export/ics${params}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) {
    throw new Error('导出 ICS 失败');
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'noto-todos.ics';
  anchor.click();
  URL.revokeObjectURL(url);
}
