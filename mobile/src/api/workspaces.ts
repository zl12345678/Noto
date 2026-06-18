import { request } from '../utils/http';

export interface Workspace {
  id: string;
  name: string;
  type: number;
  description?: string | null;
  homeNoteId?: string | null;
}

export function listWorkspaces() {
  return request<Workspace[]>({ url: '/workspaces' });
}
