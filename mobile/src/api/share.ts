import { request } from '../utils/http';

export interface ShareLink {
  token: string;
  sharePath: string;
  resourceType: 'NOTE' | 'ATTACHMENT' | 'BATCH';
  title?: string;
  enabled: boolean;
  passwordProtected?: boolean;
  viewCount?: number;
  expiresAt?: string | null;
  createdAt?: string | null;
}

export function listMyShares() {
  return request<ShareLink[]>({ url: '/share/mine' });
}

export function revokeShareByToken(token: string) {
  return request<void>({ url: `/share/links/${token}`, method: 'DELETE' });
}
