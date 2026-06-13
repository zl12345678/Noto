import http from './http';

export interface ShareLink {
  token: string;
  sharePath: string;
  resourceType: 'NOTE' | 'ATTACHMENT' | 'BATCH';
  resourceId?: string | null;
  title?: string;
  workspaceId?: string | null;
  enabled: boolean;
  passwordProtected?: boolean;
  viewCount?: number;
  lastViewedAt?: string | null;
  itemCount?: number;
  expiresAt?: string | null;
  createdAt?: string | null;
}

export interface SharedFileItem {
  id: string;
  fileName: string;
  fileType?: string;
  fileSize?: number;
  fileUrl: string;
}

export interface SharedContent {
  resourceType: 'NOTE' | 'ATTACHMENT' | 'BATCH';
  title?: string;
  content?: string;
  contentType?: string;
  fileName?: string;
  fileType?: string;
  fileSize?: number;
  fileUrl?: string;
  passwordRequired?: boolean;
  viewCount?: number;
  expiresAt?: string | null;
  sharedAt?: string | null;
  files?: SharedFileItem[];
}

export interface ShareCreateRequest {
  expiresInDays?: number | null;
  password?: string | null;
}

export interface ShareBatchCreateRequest {
  ids: string[];
  expiresInDays?: number | null;
  password?: string | null;
}

export function listMyShares() {
  return http.get<ShareLink[]>('/share/mine');
}

export function getNoteShare(noteId: string) {
  return http.get<ShareLink | null>(`/share/notes/${noteId}`);
}

export function createNoteShare(noteId: string, payload?: ShareCreateRequest) {
  return http.post<ShareLink>(`/share/notes/${noteId}`, payload ?? {});
}

export function revokeNoteShare(noteId: string) {
  return http.delete<void>(`/share/notes/${noteId}`);
}

export function getAttachmentShare(attachmentId: string) {
  return http.get<ShareLink | null>(`/share/attachments/${attachmentId}`);
}

export function createAttachmentShare(attachmentId: string, payload?: ShareCreateRequest) {
  return http.post<ShareLink>(`/share/attachments/${attachmentId}`, payload ?? {});
}

export function revokeAttachmentShare(attachmentId: string) {
  return http.delete<void>(`/share/attachments/${attachmentId}`);
}

export function createBatchAttachmentShare(payload: ShareBatchCreateRequest) {
  return http.post<ShareLink>('/share/attachments/batch', payload);
}

export function getShareInfo(token: string) {
  return http.get<ShareLink>(`/share/links/${token}`);
}

export function revokeShareByToken(token: string) {
  return http.delete<void>(`/share/links/${token}`);
}

async function readShareResponse(response: Response) {
  const body = await response.json();
  if (!response.ok || body?.code !== 0) {
    throw new Error(body?.message || '分享内容不可用');
  }
  return body.data as SharedContent;
}

export async function getPublicShare(token: string) {
  const response = await fetch(`/api/v1/share/public/${encodeURIComponent(token)}`);
  return readShareResponse(response);
}

export async function unlockPublicShare(token: string, password: string) {
  const response = await fetch(`/api/v1/share/public/${encodeURIComponent(token)}/unlock`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
  });
  return readShareResponse(response);
}

export function buildShareUrl(sharePath: string) {
  return `${window.location.origin}${sharePath}`;
}
