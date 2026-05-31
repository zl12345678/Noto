import axios from 'axios';

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('noto-zhihui-token');
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
}, (error) => {
  if (error.response?.status === 401) {
    localStorage.removeItem('noto-zhihui-token');
  }
  return Promise.reject(error);
});

export default http;
