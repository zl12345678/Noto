import { request } from '../utils/http';

export interface Tag {
  id: string;
  workspaceId: string;
  name: string;
  color?: string | null;
}

export function listTags(workspaceId: string) {
  return request<Tag[]>({ url: '/tags', params: { workspaceId } });
}

export function createTag(payload: { workspaceId: string; name: string; color?: string }) {
  return request<Tag>({ url: '/tags', method: 'POST', data: payload });
}

export function deleteTag(id: string) {
  return request<void>({ url: `/tags/${id}`, method: 'DELETE' });
}
