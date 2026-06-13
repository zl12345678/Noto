import http from './http';

export interface Tag {
  id: string;
  workspaceId: string;
  name: string;
  color?: string | null;
}

export interface TagCreateRequest {
  workspaceId: string;
  name: string;
  color?: string;
}

export function listTags(workspaceId: string) {
  return http.get<Tag[]>('/tags', { params: { workspaceId } });
}

export function createTag(payload: TagCreateRequest) {
  return http.post<Tag>('/tags', payload);
}

export function deleteTag(id: string) {
  return http.delete<void>(`/tags/${id}`);
}
