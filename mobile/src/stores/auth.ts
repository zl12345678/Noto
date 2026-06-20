import { reactive } from 'vue';
import { getCurrentUser, login as loginApi, logout as logoutApi, register as registerApi } from '../api/auth';
import { clearToken, setToken } from '../utils/http';
import { redirectToLogin } from '../utils/navigation';
import type { CurrentUser, LoginRequest } from '../types/auth';

const USER_KEY = 'noto-current-user';

function loadCachedUser(): CurrentUser | null {
  try {
    const raw = uni.getStorageSync(USER_KEY);
    return raw ? JSON.parse(raw) as CurrentUser : null;
  } catch {
    return null;
  }
}

function cacheUser(user: CurrentUser | null) {
  if (user) {
    uni.setStorageSync(USER_KEY, JSON.stringify(user));
  } else {
    uni.removeStorageSync(USER_KEY);
  }
}

export const authState = reactive({
  token: uni.getStorageSync('noto-zhihui-token') || '',
  user: loadCachedUser() as CurrentUser | null,
});

export function isAuthenticated() {
  return Boolean(authState.token);
}

export async function login(payload: LoginRequest) {
  const data = await loginApi(payload);
  authState.token = data.token;
  authState.user = data.user;
  setToken(data.token);
  cacheUser(data.user);
}

export async function register(payload: {
  username: string;
  email: string;
  nickname: string;
  password: string;
}) {
  const data = await registerApi(payload);
  authState.token = data.token;
  authState.user = data.user;
  setToken(data.token);
  cacheUser(data.user);
}

export async function fetchCurrentUser() {
  const user = await getCurrentUser();
  authState.user = user;
  cacheUser(user);
}

export async function logout() {
  try {
    await logoutApi();
  } finally {
    authState.token = '';
    authState.user = null;
    clearToken();
    cacheUser(null);
  }
}

export function ensureAuthPage() {
  if (!isAuthenticated()) {
    redirectToLogin();
    return false;
  }
  return true;
}
