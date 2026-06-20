import { request } from '../utils/http';

export interface Folder {
  id: string;
  workspaceId: string;
  parentId?: string | null;
  name: string;
  sortOrder: number;
  createdAt?: string;
}

export function listFolders(workspaceId: string) {
  return request<Folder[]>({ url: '/folders', params: { workspaceId } });
}

export function createFolder(payload: { workspaceId: string; parentId?: string | null; name: string }) {
  return request<Folder>({ url: '/folders', method: 'POST', data: payload });
}

export function updateFolder(id: string, payload: { name: string; parentId?: string | null; sortOrder?: number }) {
  return request<Folder>({ url: `/folders/${id}`, method: 'PUT', data: payload });
}

export function patchFolderTree(id: string, payload: { parentId?: string | null; sortOrder: number }) {
  return request<Folder>({ url: `/folders/${id}/tree`, method: 'PATCH', data: payload });
}

export function deleteFolder(id: string) {
  return request<void>({ url: `/folders/${id}`, method: 'DELETE' });
}
