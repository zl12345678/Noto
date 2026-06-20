import { getApiBaseUrl } from './config';

export function resolveFileUrl(url?: string) {
  if (!url) return '';
  if (url.startsWith('http')) return url;
  const base = getApiBaseUrl().replace(/\/api\/v1$/, '');
  return `${base}${url.startsWith('/') ? url : `/${url}`}`;
}

export function openExternalUrl(url?: string, title = '打开文件') {
  const resolved = resolveFileUrl(url);
  if (!resolved) {
    uni.showToast({ title: '文件地址为空', icon: 'none' });
    return;
  }

  // #ifdef H5
  window.open(resolved, '_blank');
  // #endif

  // #ifndef H5
  const runtime = typeof plus !== 'undefined' ? plus.runtime : null;
  if (runtime?.openURL) {
    uni.showModal({
      title,
      content: '是否在浏览器中打开？',
      success: (res) => {
        if (res.confirm) runtime.openURL(resolved);
      },
    });
    return;
  }
  uni.setClipboardData({
    data: resolved,
    success: () => uni.showToast({ title: '链接已复制', icon: 'none' }),
    fail: () => uni.showToast({ title: '当前平台无法打开文件', icon: 'none' }),
  });
  // #endif
}
