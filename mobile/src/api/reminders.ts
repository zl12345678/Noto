import { request } from '../utils/http';

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
}

export interface ReminderPage {
  records: ReminderItem[];
  total: number;
}

export const REMINDER_STATUS = { PENDING: 0, SENT: 1, CANCELLED: 2 } as const;

export const REMINDER_STATUS_LABEL: Record<number, string> = {
  0: '待触发',
  1: '已触发',
  2: '已取消',
};

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

export function listReminders(params: {
  page?: number;
  size?: number;
  workspaceId?: string | null;
  status?: number | null;
  todoId?: string | null;
}) {
  return request<ReminderPage>({ url: '/reminders', params });
}

export function fetchDueReminders() {
  return request<ReminderItem[]>({ url: '/reminders/due' });
}

export function createReminder(payload: ReminderCreateRequest) {
  return request<ReminderItem>({ url: '/reminders', method: 'POST', data: payload });
}

export function updateReminder(id: string, payload: ReminderUpdateRequest) {
  return request<ReminderItem>({ url: `/reminders/${id}`, method: 'PUT', data: payload });
}

export function cancelReminder(id: string) {
  return request<ReminderItem>({ url: `/reminders/${id}/cancel`, method: 'PATCH' });
}

export function deleteReminder(id: string) {
  return request<void>({ url: `/reminders/${id}`, method: 'DELETE' });
}
