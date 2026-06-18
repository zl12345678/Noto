import { request } from '../utils/http';
import type { CurrentUser, LoginRequest, LoginResponse } from '../types/auth';

export function login(payload: LoginRequest) {
  return request<LoginResponse>({ url: '/auth/login', method: 'POST', data: payload, auth: false });
}

export function getCurrentUser() {
  return request<CurrentUser>({ url: '/auth/me' });
}

export function logout() {
  return request<null>({ url: '/auth/logout', method: 'POST' });
}

export function register(payload: {
  username: string;
  email: string;
  nickname: string;
  password: string;
}) {
  return request<import('../types/auth').LoginResponse>({
    url: '/auth/register',
    method: 'POST',
    data: payload,
    auth: false,
  });
}
