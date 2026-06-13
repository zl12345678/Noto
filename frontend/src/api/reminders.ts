import http from './http';

export interface ReminderItem {
  id: string;
  workspaceId: string;
  workspaceName?: string | null;
  todoId: string;
  todoTitle?: string | null;
  reminderType: string;
  triggerAt: string;
  message?: string | null;
  status: number;
  sentAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
  noteId?: string | null;
  noteTitle?: string | null;
  noteContext?: string | null;
}

export interface ReminderPage {
  records: ReminderItem[];
  total: number;
  size: number;
  current: number;
}

export interface ReminderCreateRequest {
  workspaceId: string;
  todoId: string;
  reminderType?: string;
  triggerAt: string;
  message?: string;
}

export interface ReminderUpdateRequest {
  reminderType?: string;
  triggerAt?: string;
  message?: string;
}

export const REMINDER_STATUS = {
  PENDING: 0,
  SENT: 1,
  CANCELLED: 2,
} as const;

export const REMINDER_STATUS_LABEL: Record<number, string> = {
  0: '待触发',
  1: '已触发',
  2: '已取消',
};

export function listReminders(params: {
  page?: number;
  size?: number;
  workspaceId?: string | null;
  status?: number | null;
  todoId?: string | null;
}) {
  return http.get<ReminderPage>('/reminders', { params });
}

export function fetchDueReminders() {
  return http.get<ReminderItem[]>('/reminders/due');
}

export function createReminder(payload: ReminderCreateRequest) {
  return http.post<ReminderItem>('/reminders', payload);
}

export function updateReminder(id: string, payload: ReminderUpdateRequest) {
  return http.put<ReminderItem>(`/reminders/${id}`, payload);
}

export function cancelReminder(id: string) {
  return http.patch<ReminderItem>(`/reminders/${id}/cancel`);
}

export function deleteReminder(id: string) {
  return http.delete<void>(`/reminders/${id}`);
}
