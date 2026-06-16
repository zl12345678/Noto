/** 与 src/api/http.ts 一致，供 fetch/SSE 等非 axios 请求使用 */
export function getApiBaseUrl(): string {
  const raw = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '');
  return raw || '/api/v1';
}