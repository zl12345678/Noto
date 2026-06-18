import { request } from '../utils/http';

export interface SearchResult {
  noteId: string;
  title: string;
  snippet: string;
  highlight: string;
  workspaceName: string | null;
}

export interface SearchPage {
  records: SearchResult[];
  total: number;
}

export function searchNotes(params: { keyword: string; page?: number; size?: number }) {
  return request<SearchPage>({ url: '/search', params });
}
