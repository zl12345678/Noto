import http from './http';
import type { Note } from './notes';
import type { TodoItem } from './todos';

export type { Note };

export interface DashboardStats {
  /** 用户文档数（不含各知识库自动生成的首页） */
  totalNotes: number;
  favoriteNotes: number;
  archivedNotes: number;
  totalTodos: number;
  pendingTodos: number;
  completedTodos: number;
  overdueTodos: number;
  recentNotes: Note[];
  actionTodos: TodoItem[];
  parallelTodos: TodoItem[];
  longTermTodos: TodoItem[];
  parallelActiveCount: number;
  longTermActiveCount: number;
  todayCompletedTodos: number;
  /** 是否适合展示新手引导 */
  onboardingEligible?: boolean;
}

export function getDashboardStats() {
  return http.get<DashboardStats>('/dashboard/stats');
}
