import { request } from '../utils/http';
import { getApiBaseUrl } from '../utils/config';

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

export interface ShareCreateRequest {
  expiresInDays?: number | null;
  password?: string | null;
}

export interface ShareBatchCreateRequest extends ShareCreateRequest {
  ids: string[];
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

export function listMyShares() {
  return request<ShareLink[]>({ url: '/share/mine' });
}

export function getNoteShare(noteId: string) {
  return request<ShareLink | null>({ url: `/share/notes/${noteId}` });
}

export function createNoteShare(noteId: string, payload?: ShareCreateRequest) {
  return request<ShareLink>({ url: `/share/notes/${noteId}`, method: 'POST', data: payload ?? {} });
}

export function revokeNoteShare(noteId: string) {
  return request<void>({ url: `/share/notes/${noteId}`, method: 'DELETE' });
}

export function getAttachmentShare(attachmentId: string) {
  return request<ShareLink | null>({ url: `/share/attachments/${attachmentId}` });
}

export function createAttachmentShare(attachmentId: string, payload?: ShareCreateRequest) {
  return request<ShareLink>({ url: `/share/attachments/${attachmentId}`, method: 'POST', data: payload ?? {} });
}

export function createBatchAttachmentShare(payload: ShareBatchCreateRequest) {
  return request<ShareLink>({ url: '/share/attachments/batch', method: 'POST', data: payload });
}

export function revokeAttachmentShare(attachmentId: string) {
  return request<void>({ url: `/share/attachments/${attachmentId}`, method: 'DELETE' });
}

export function revokeShareByToken(token: string) {
  return request<void>({ url: `/share/links/${token}`, method: 'DELETE' });
}

export function getPublicShare(token: string) {
  return request<SharedContent>({ url: `/share/public/${encodeURIComponent(token)}`, auth: false });
}

export function unlockPublicShare(token: string, password: string) {
  return request<SharedContent>({
    url: `/share/public/${encodeURIComponent(token)}/unlock`,
    method: 'POST',
    data: { password },
    auth: false,
  });
}

export function buildShareUrl(sharePath: string) {
  const path = sharePath.startsWith('/') ? sharePath : `/${sharePath}`;
  const base = getApiBaseUrl().replace(/\/api\/v1$/, '');
  return `${base}${path}`;
}

export function tokenFromSharePath(sharePath: string) {
  const segments = sharePath.split('/').filter(Boolean);
  return segments[segments.length - 1] || sharePath;
}
