import http from './http';
import type { CurrentUser, LoginRequest, LoginResponse } from '../types/auth';

export interface RegisterRequest {
  username: string;
  email: string;
  nickname: string;
  password: string;
}

export interface ResetPasswordRequest {
  email: string;
  newPassword: string;
}

export function login(payload: LoginRequest) {
  return http.post<LoginResponse>('/auth/login', payload);
}

export function getCurrentUser() {
  return http.get<CurrentUser>('/auth/me');
}

export function register(payload: RegisterRequest) {
  return http.post<LoginResponse>('/auth/register', payload);
}

export function forgotPassword(payload: ResetPasswordRequest) {
  return http.post<null>('/auth/forgot-password', payload);
}

export function logout() {
  return http.post<null>('/auth/logout');
}
