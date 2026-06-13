import http from './http';
import type { CurrentUser } from '../types/auth';

export interface UpdateAvatarRequest {
  avatarUrl: string;
}

export function updateAvatar(payload: UpdateAvatarRequest) {
  return http.post<CurrentUser>('/auth/avatar', payload);
}
