import { request } from '../utils/http';

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
  todayCompletedTodos: number;
}

export const TODO_STATUS = {
  PENDING: 0,
  IN_PROGRESS: 1,
  COMPLETED: 2,
} as const;

export const TODO_STATUS_LABEL: Record<number, string> = {
  0: '待开始',
  1: '进行中',
  2: '已完成',
  3: '已取消',
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

export function createTodo(payload: {
  workspaceId: string;
  title: string;
  description?: string;
  priority?: number;
  horizon?: TodoHorizonType;
}) {
  return request<TodoItem>({ url: '/todos', method: 'POST', data: payload });
}

export function listTodos(params: { page?: number; size?: number; status?: number | null }) {
  return request<{ records: TodoItem[]; total: number }>({ url: '/todos', params });
}

export function isTodoOverdue(item: TodoItem) {
  if (!item.dueAt || item.status === TODO_STATUS.COMPLETED) return false;
  return new Date(item.dueAt).getTime() < Date.now();
}
