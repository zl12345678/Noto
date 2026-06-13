const STORAGE_KEY = 'noto-browser-notify';

export function isBrowserNotifyEnabled(): boolean {
  return localStorage.getItem(STORAGE_KEY) === '1';
}

export function setBrowserNotifyEnabled(enabled: boolean) {
  if (enabled) {
    localStorage.setItem(STORAGE_KEY, '1');
  } else {
    localStorage.removeItem(STORAGE_KEY);
  }
}

export function isBrowserNotifySupported(): boolean {
  return typeof Notification !== 'undefined';
}

export function getBrowserNotifyPermission(): NotificationPermission | 'unsupported' {
  if (!isBrowserNotifySupported()) return 'unsupported';
  return Notification.permission;
}

export async function requestBrowserNotifyPermission(): Promise<NotificationPermission | 'unsupported'> {
  if (!isBrowserNotifySupported()) return 'unsupported';
  if (Notification.permission === 'granted') return 'granted';
  if (Notification.permission === 'denied') return 'denied';
  return Notification.requestPermission();
}

export function showBrowserReminderNotification(title: string, body: string) {
  if (!isBrowserNotifyEnabled()) return;
  if (!isBrowserNotifySupported()) return;
  if (Notification.permission !== 'granted') return;
  if (document.visibilityState === 'visible') return;
  try {
    new Notification(title, {
      body,
      tag: `noto-reminder-${Date.now()}`,
    });
  } catch {
    // 部分环境禁止 Notification
  }
}
