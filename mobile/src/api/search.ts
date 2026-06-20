import { request } from '../utils/http';

export interface SearchResult {
  noteId: string;
  title: string;
  snippet: string;
  highlight: string;
  workspaceName: string | null;
  folderId?: string | null;
  tags?: Array<{ id: string; name: string; color?: string | null }>;
}

export interface SearchPage {
  records: SearchResult[];
  total: number;
}

export function searchNotes(params: {
  keyword: string;
  page?: number;
  size?: number;
  workspaceId?: string | null;
  folderId?: string | null;
  tagId?: string | null;
}) {
  return request<SearchPage>({ url: '/search', params });
}
