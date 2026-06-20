import { request } from '../utils/http';

export interface Workspace {
  id: string;
  name: string;
  type: number;
  description?: string | null;
  status?: number;
  homeNoteId?: string | null;
  createdAt?: string;
}

export function listWorkspaces() {
  return request<Workspace[]>({ url: '/workspaces' });
}

export function createWorkspace(payload: { name: string; description?: string }) {
  return request<Workspace>({ url: '/workspaces', method: 'POST', data: payload });
}

export function updateWorkspace(id: string, payload: { name: string; description?: string }) {
  return request<Workspace>({ url: `/workspaces/${id}`, method: 'PUT', data: payload });
}

export function deleteWorkspace(id: string) {
  return request<void>({ url: `/workspaces/${id}`, method: 'DELETE' });
}
