import { getAuthToken, request } from '../utils/http';
import { getApiBaseUrl } from '../utils/config';
import type { Tag } from './tags';

export interface Note {
  id: string;
  workspaceId: string | null;
  folderId: string | null;
  parentId?: string | null;
  sortOrder?: number;
  title: string;
  content: string;
  excerpt?: string | null;
  summary?: string | null;
  isFavorite: boolean;
  status?: number;
  tags?: Tag[];
  lastEditedAt?: string | null;
  updatedAt?: string;
}

export interface NotePage {
  records: Note[];
  total: number;
  size: number;
  current: number;
}

export interface NoteImportResult {
  noteId: string;
  noteTitle: string;
  structured: boolean;
}

export interface NoteClipImportRequest {
  workspaceId: string;
  title: string;
  content: string;
  sourceUrl?: string;
  contentType?: 'plain' | 'markdown' | 'html';
  folderId?: string | null;
  structureWithAi?: boolean;
}

export function listNotes(params: {
  page?: number;
  size?: number;
  keyword?: string;
  workspaceId?: string | null;
  folderId?: string | null;
}) {
  return request<NotePage>({ url: '/notes', params });
}

export function getNote(id: string) {
  return request<Note>({ url: `/notes/${id}` });
}

export function createNote(payload: {
  title: string;
  content?: string;
  workspaceId: string;
  contentType?: string;
  folderId?: string | null;
  parentId?: string | null;
}) {
  return request<Note>({ url: '/notes', method: 'POST', data: payload });
}

export function updateNote(id: string, payload: {
  title: string;
  content: string;
  contentType?: string;
  folderId?: string | null;
  parentId?: string | null;
  status?: number;
  isFavorite?: boolean;
  summary?: string | null;
  tagIds?: string[];
  autoSummary?: boolean;
}) {
  return request<Note>({ url: `/notes/${id}`, method: 'PUT', data: payload });
}

export function patchNoteFavorite(id: string, isFavorite: boolean) {
  return request<Note>({ url: `/notes/${id}/favorite`, method: 'PATCH', data: { isFavorite } });
}

export function patchNoteStatus(id: string, status: number) {
  return request<Note>({ url: `/notes/${id}/status`, method: 'PATCH', data: { status } });
}

export function patchNoteTree(id: string, payload: { folderId?: string | null; parentId?: string | null; sortOrder: number }) {
  return request<Note>({ url: `/notes/${id}/tree`, method: 'PATCH', data: payload });
}

export function deleteNote(id: string) {
  return request<void>({ url: `/notes/${id}`, method: 'DELETE' });
}

export function listRelatedNotes(id: string, limit = 5) {
  return request<Note[]>({ url: `/notes/${id}/related`, params: { limit } });
}

export function importNoteClip(payload: NoteClipImportRequest) {
  return request<NoteImportResult>({ url: '/notes/import', method: 'POST', data: payload });
}

export function importNoteFilePath(payload: {
  workspaceId: string;
  filePath: string;
  folderId?: string | null;
  title?: string;
  name?: string;
}) {
  const params = new URLSearchParams({ workspaceId: payload.workspaceId });
  if (payload.folderId) params.set('folderId', payload.folderId);
  if (payload.title?.trim()) params.set('title', payload.title.trim());
  const url = `${getApiBaseUrl()}/notes/import/file?${params.toString()}`;

  return new Promise<NoteImportResult>((resolve, reject) => {
    (uni as any).uploadFile({
      url,
      filePath: payload.filePath,
      name: 'file',
      fileName: payload.name,
      header: getAuthToken() ? { Authorization: `Bearer ${getAuthToken()}` } : {},
      success: (res: any) => {
        const status = res.statusCode || 0;
        let body: any = res.data;
        if (typeof body === 'string') {
          try {
            body = JSON.parse(body);
          } catch {
            body = { message: body };
          }
        }
        if (status < 200 || status >= 300) {
          reject(new Error(body?.message || `导入失败 (${status})`));
          return;
        }
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code !== 0) {
            reject(new Error(body.message || '导入失败'));
            return;
          }
          resolve(body.data as NoteImportResult);
          return;
        }
        resolve(body as NoteImportResult);
      },
      fail: () => reject(new Error('导入失败')),
    });
  });
}
