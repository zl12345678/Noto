import http from './http';

export interface AdminAuditLog {
  id: string;
  userId?: string;
  username?: string;
  nickname?: string;
  workspaceId?: string;
  actionType: string;
  resourceType?: string;
  resourceId?: string;
  ipAddress?: string;
  userAgent?: string;
  detail?: Record<string, unknown>;
  createdAt: string;
}

export interface AdminAuditSummary {
  totalActions: number;
  todayActions: number;
  uniqueUsers: number;
  todayUniqueUsers: number;
  uniqueIps: number;
  todayUniqueIps: number;
}

export interface AdminAuditPage {
  records: AdminAuditLog[];
  total: number;
  current: number;
  size: number;
}

export function listAdminAuditLogs(params?: {
  page?: number;
  size?: number;
  userId?: string;
  actionType?: string;
  ipAddress?: string;
  startAt?: string;
  endAt?: string;
}) {
  return http.get<AdminAuditPage>('/admin/audit-logs', { params });
}

export function getAdminAuditSummary(params?: { startAt?: string; endAt?: string }) {
  return http.get<AdminAuditSummary>('/admin/audit-logs/summary', { params });
}
