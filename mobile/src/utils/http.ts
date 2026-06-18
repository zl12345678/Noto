import { getApiBaseUrl } from './config';

const TOKEN_KEY = 'noto-zhihui-token';

export class ApiError extends Error {
  status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.status = status;
  }
}

function getToken(): string {
  return uni.getStorageSync(TOKEN_KEY) || '';
}

export function setToken(token: string) {
  if (token) {
    uni.setStorageSync(TOKEN_KEY, token);
  } else {
    uni.removeStorageSync(TOKEN_KEY);
  }
}

export function clearToken() {
  uni.removeStorageSync(TOKEN_KEY);
}

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return '';
  const parts: string[] = [];
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  });
  return parts.length ? `?${parts.join('&')}` : '';
}

export function request<T>(options: {
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  data?: unknown;
  params?: Record<string, unknown>;
  auth?: boolean;
}): Promise<T> {
  const method = options.method || 'GET';
  const auth = options.auth !== false;
  const query = method === 'GET' ? buildQuery(options.params) : '';
  const url = `${getApiBaseUrl()}${options.url}${query}`;

  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method,
      data: method === 'GET' ? undefined : options.data,
      header: {
        'Content-Type': 'application/json',
        ...(auth && getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
      },
      success: (res) => {
        const status = res.statusCode || 0;
        const body = res.data as any;

        if (status === 401) {
          clearToken();
          uni.reLaunch({ url: '/pages/login/login' });
          reject(new ApiError('登录已过期，请重新登录', 401));
          return;
        }

        if (status < 200 || status >= 300) {
          const message = body?.message || `请求失败 (${status})`;
          reject(new ApiError(message, status));
          return;
        }

        if (body && typeof body === 'object' && 'code' in body && 'message' in body) {
          if (body.code !== 0) {
            reject(new ApiError(body.message || '请求失败'));
            return;
          }
          resolve(body.data as T);
          return;
        }

        resolve(body as T);
      },
      fail: () => {
        reject(new ApiError('网络异常，请稍后重试'));
      },
    });
  });
}
