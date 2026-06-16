import axios from 'axios';
import router from '../router';
import { useAuthStore } from '../store/auth';

import { getApiBaseUrl } from '../utils/apiBase';

const http = axios.create({
  baseURL: getApiBaseUrl(),
  timeout: 15000,
});

let handlingAuthFailure = false;

async function handleUnauthorized() {
  if (handlingAuthFailure) {
    return;
  }
  handlingAuthFailure = true;
  try {
    const authStore = useAuthStore();
    await authStore.logout();
    if (router.currentRoute.value.meta.public !== true) {
      await router.push('/login');
    }
  } finally {
    handlingAuthFailure = false;
  }
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('noto-zhihui-token') || sessionStorage.getItem('noto-zhihui-token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use((response) => {
  const body = response.data;
  if (body && typeof body === 'object' && 'code' in body && 'message' in body) {
    if (body.code !== 0) {
      const error = new Error(body.message || '请求失败') as Error & { response?: { data: any } };
      error.response = { data: body };
      return Promise.reject(error);
    }
    return body.data;
  }
  return body;
}, async (error) => {
  const status = error.response?.status;
  if (status === 401) {
    await handleUnauthorized();
  }
  const body = error.response?.data;
  if (body && typeof body === 'object' && typeof body.message === 'string' && body.message) {
    error.message = body.message;
  } else if (!error.message || error.message === 'Network Error') {
    error.message = '网络异常，请稍后重试';
  }
  return Promise.reject(error);
});

export default http;
