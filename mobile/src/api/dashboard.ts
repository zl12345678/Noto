import { request } from '../utils/http';
import type { Note } from './notes';
import type { TodoItem } from './todos';

export interface DashboardStats {
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
  onboardingEligible?: boolean;
}

export function getDashboardStats() {
  return request<DashboardStats>({ url: '/dashboard/stats' });
}
