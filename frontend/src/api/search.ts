import http from './http';
import type { Tag } from './tags';

export interface SearchResult {
  noteId: string;
  title: string;
  snippet: string;
  highlight: string;
  workspaceId: string | null;
  workspaceName: string | null;
  folderId: string | null;
  lastEditedAt?: string | null;
  tags?: Tag[];
  offsetStart?: number | null;
  offsetEnd?: number | null;
}

export interface SearchPage {
  records: SearchResult[];
  total: number;
  size: number;
  current: number;
}

export function searchNotes(params: {
  keyword: string;
  page?: number;
  size?: number;
  workspaceId?: string | null;
  folderId?: string | null;
  tagId?: string | null;
}) {
  return http.get<SearchPage>('/search', { params });
}
