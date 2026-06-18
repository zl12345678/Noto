import { request } from '../utils/http';
import type { TodoItem } from './todos';

export interface DashboardStats {
  totalNotes: number;
  favoriteNotes: number;
  archivedNotes: number;
  totalTodos: number;
  pendingTodos: number;
  completedTodos: number;
  overdueTodos: number;
  actionTodos: TodoItem[];
  parallelTodos: TodoItem[];
  longTermTodos: TodoItem[];
  parallelActiveCount: number;
  longTermActiveCount: number;
  todayCompletedTodos: number;
}

export function getDashboardStats() {
  return request<DashboardStats>({ url: '/dashboard/stats' });
}
