import { request } from '../utils/http';
import type { CurrentUser } from '../types/auth';

export function updateProfile(payload: { nickname: string }) {
  return request<CurrentUser>({ url: '/auth/profile', method: 'POST', data: payload });
}

export function updateAvatar(payload: { avatarUrl: string }) {
  return request<CurrentUser>({ url: '/auth/avatar', method: 'POST', data: payload });
}

export function changePassword(payload: { oldPassword: string; newPassword: string }) {
  return request<null>({ url: '/auth/change-password', method: 'POST', data: payload });
}
