import http from './http';
import type { CurrentUser } from '../types/auth';

export interface UpdateProfileRequest {
  nickname: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

export function updateProfile(payload: UpdateProfileRequest) {
  return http.post<CurrentUser>('/auth/profile', payload);
}

export function changePassword(payload: ChangePasswordRequest) {
  return http.post<null>('/auth/change-password', payload);
}
