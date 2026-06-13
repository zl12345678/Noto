import http from './http';

export interface Workspace {
  id: string;
  name: string;
  type: number;
  description?: string | null;
  status: number;
  homeNoteId?: string | null;
  createdAt?: string;
}

export interface WorkspaceCreateRequest {
  name: string;
  description?: string;
}

export interface WorkspaceUpdateRequest {
  name: string;
  description?: string;
}

export function listWorkspaces() {
  return http.get<Workspace[]>('/workspaces');
}

export function createWorkspace(payload: WorkspaceCreateRequest) {
  return http.post<Workspace>('/workspaces', payload);
}

export function updateWorkspace(id: string, payload: WorkspaceUpdateRequest) {
  return http.put<Workspace>(`/workspaces/${id}`, payload);
}

export function deleteWorkspace(id: string) {
  return http.delete<void>(`/workspaces/${id}`);
}
