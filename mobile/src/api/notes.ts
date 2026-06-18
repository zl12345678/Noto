import { request } from '../utils/http';

export interface Note {
  id: string;
  workspaceId: string | null;
  folderId: string | null;
  title: string;
  content: string;
  excerpt?: string | null;
  summary?: string | null;
  isFavorite: boolean;
  lastEditedAt?: string | null;
  updatedAt?: string;
}

export interface NotePage {
  records: Note[];
  total: number;
  size: number;
  current: number;
}

export function listNotes(params: {
  page?: number;
  size?: number;
  keyword?: string;
  workspaceId?: string | null;
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
}) {
  return request<Note>({ url: '/notes', method: 'POST', data: payload });
}

export function updateNote(id: string, payload: { title: string; content: string }) {
  return request<Note>({ url: `/notes/${id}`, method: 'PUT', data: payload });
}

export function patchNoteFavorite(id: string, isFavorite: boolean) {
  return request<Note>({ url: `/notes/${id}/favorite`, method: 'PATCH', data: { isFavorite } });
}
