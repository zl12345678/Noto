import http from './http';

export interface Folder {
  id: string;
  workspaceId: string;
  parentId?: string | null;
  name: string;
  sortOrder: number;
  createdAt?: string;
}

export interface FolderCreateRequest {
  workspaceId: string;
  parentId?: string | null;
  name: string;
}

export interface FolderUpdateRequest {
  name: string;
  parentId?: string | null;
  sortOrder?: number | null;
}

export function listFolders(workspaceId: string) {
  return http.get<Folder[]>('/folders', { params: { workspaceId } });
}

export function createFolder(payload: FolderCreateRequest) {
  return http.post<Folder>('/folders', payload);
}

export function updateFolder(id: string, payload: FolderUpdateRequest) {
  return http.put<Folder>(`/folders/${id}`, payload);
}

export function deleteFolder(id: string) {
  return http.delete<void>(`/folders/${id}`);
}

export interface FolderTreeMoveRequest {
  parentId?: string | null;
  sortOrder: number;
}

export function patchFolderTree(id: string, payload: FolderTreeMoveRequest) {
  return http.patch<Folder>(`/folders/${id}/tree`, payload);
}
