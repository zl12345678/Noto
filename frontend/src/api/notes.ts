import http from './http';
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
  contentType: string;
  summary?: string | null;
  status: number;
  isFavorite: boolean;
  lastEditedAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
  tags?: Tag[];
}

export interface NotePage {
  records: Note[];
  total: number;
  size: number;
  current: number;
}

export interface NoteCreateRequest {
  title: string;
  content: string;
  contentType?: string;
  workspaceId?: string | null;
  folderId?: string | null;
  parentId?: string | null;
  tagIds?: string[];
}

export interface NoteUpdateRequest {
  title: string;
  content: string;
  contentType?: string;
  folderId?: string | null;
  parentId?: string | null;
  status?: number | null;
  isFavorite?: boolean | null;
  summary?: string | null;
  tagIds?: string[];
  autoSummary?: boolean;
}

export function listNotes(params: {
  page?: number;
  size?: number;
  keyword?: string;
  status?: number | null;
  isFavorite?: boolean | null;
  workspaceId?: string | null;
  folderId?: string | null;
  tagId?: string | null;
}) {
  return http.get<NotePage>('/notes', { params });
}

export function getNote(id: string) {
  return http.get<Note>(`/notes/${id}`);
}

export function createNote(payload: NoteCreateRequest) {
  return http.post<Note>('/notes', payload);
}

export function updateNote(id: string, payload: NoteUpdateRequest) {
  return http.put<Note>(`/notes/${id}`, payload);
}

export function patchNoteFavorite(id: string, isFavorite: boolean) {
  return http.patch<Note>(`/notes/${id}/favorite`, { isFavorite });
}

export function patchNoteStatus(id: string, status: number) {
  return http.patch<Note>(`/notes/${id}/status`, { status });
}

export function notePreviewText(note: Note) {
  return note.summary || note.excerpt || note.content || '暂无摘要';
}

export function deleteNote(id: string) {
  return http.delete<void>(`/notes/${id}`);
}

export function listRelatedNotes(id: string, limit = 5) {
  return http.get<Note[]>(`/notes/${id}/related`, { params: { limit } });
}

export interface NoteTreeMoveRequest {
  folderId?: string | null;
  parentId?: string | null;
  sortOrder: number;
}

export function patchNoteTree(id: string, payload: NoteTreeMoveRequest) {
  return http.patch<Note>(`/notes/${id}/tree`, payload);
}

export type { ConfirmExtractTodoItem, NoteExtractTodosPreview } from './ai';
export type { NoteExtractTodosResult } from './ai';

import type { ConfirmExtractTodoItem, NoteExtractTodosPreview, NoteExtractTodosResult } from './ai';

/** Markdown 规则提取预览（不入库） */
export function previewExtractTodosFromNote(id: string) {
  return http.post<NoteExtractTodosPreview>(`/notes/${id}/extract-todos`);
}

export function confirmExtractTodosFromNote(id: string, items: ConfirmExtractTodoItem[]) {
  return http.post<NoteExtractTodosResult>(`/notes/${id}/extract-todos/confirm`, { items });
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

export interface NoteImportResult {
  noteId: string;
  noteTitle: string;
  structured: boolean;
}

export function importNoteClip(payload: NoteClipImportRequest) {
  return http.post<NoteImportResult>('/notes/import', payload);
}

export function importNoteFile(
  workspaceId: string,
  file: File,
  folderId?: string | null,
  title?: string,
) {
  const formData = new FormData();
  formData.append('file', file);
  return http.post<NoteImportResult>('/notes/import/file', formData, {
    params: {
      workspaceId,
      ...(folderId ? { folderId } : {}),
      ...(title ? { title } : {}),
    },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  });
}
