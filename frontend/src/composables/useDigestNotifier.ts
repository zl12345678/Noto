import { onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { h } from 'vue';
import { Button, notification } from 'ant-design-vue';
import { getCurrentWeeklyRetro, getTodayAiDigest, type AiDigest, type AiWeeklyRetro } from '../api/ai';
import { getAiUserSettings } from '../api/settings';
import { useAuthStore } from '../store/auth';
import { showBrowserReminderNotification } from '../utils/browserNotification';

const POLL_MS = 5 * 60 * 1000;
const DIGEST_TASK_KEY = 'noto-digest-notified-task-id';
const WEEKLY_TASK_KEY = 'noto-weekly-notified-task-id';

function readStoredTaskId(key: string) {
  return localStorage.getItem(key) || '';
}

function storeTaskId(key: string, taskId: string) {
  localStorage.setItem(key, taskId);
}

function showDigestReadyNotice(digest: AiDigest, router: ReturnType<typeof useRouter>) {
  const summary = digest.suggestions?.summary?.trim() || '今日行动建议已生成，可在首页查看';
  const key = `digest-${digest.taskId || digest.digestDate || Date.now()}`;
  showBrowserReminderNotification('今日行动建议已就绪', summary);
  notification.open({
    key,
    message: '今日行动建议已就绪',
    description: summary,
    duration: 12,
    btn: () =>
      h(
        Button,
        {
          type: 'primary',
          size: 'small',
          onClick: () => {
            notification.close(key);
            router.push('/');
          },
        },
        () => '去首页查看',
      ),
  });
}

function showWeeklyRetroReadyNotice(retro: AiWeeklyRetro, router: ReturnType<typeof useRouter>) {
  const title = retro.noteTitle?.trim() || '本周复盘笔记';
  const key = `weekly-${retro.taskId || retro.weekStart || Date.now()}`;
  showBrowserReminderNotification('本周复盘已生成', title);
  notification.open({
    key,
    message: '本周复盘已生成',
    description: title,
    duration: 12,
    btn: () =>
      h(
        'div',
        { style: 'display:flex;gap:8px' },
        [
          h(
            Button,
            {
              type: 'primary',
              size: 'small',
              onClick: () => {
                notification.close(key);
                if (retro.noteId) {
                  router.push({ path: `/notes/${retro.noteId}`, query: { from: 'dashboard' } });
                } else {
                  router.push('/');
                }
              },
            },
            () => '打开笔记',
          ),
          h(
            Button,
            {
              size: 'small',
              onClick: () => {
                notification.close(key);
                router.push('/');
              },
            },
            () => '去首页',
          ),
        ],
      ),
  });
}

async function syncDigestNotifications(
  router: ReturnType<typeof useRouter>,
  options: { initializeBaseline?: boolean } = {},
) {
  const settings = await getAiUserSettings();
  const { initializeBaseline = false } = options;

  if (settings.dailyDigestEnabled) {
    const digest = await getTodayAiDigest();
    const taskId = digest?.taskId ? String(digest.taskId) : '';
    if (taskId) {
      const last = readStoredTaskId(DIGEST_TASK_KEY);
      if (initializeBaseline || !last) {
        storeTaskId(DIGEST_TASK_KEY, taskId);
      } else if (last !== taskId) {
        storeTaskId(DIGEST_TASK_KEY, taskId);
        showDigestReadyNotice(digest!, router);
      }
    }
  }

  if (settings.weeklyRetroEnabled) {
    const retro = await getCurrentWeeklyRetro();
    const taskId = retro?.taskId ? String(retro.taskId) : '';
    if (taskId && retro?.noteId) {
      const last = readStoredTaskId(WEEKLY_TASK_KEY);
      if (initializeBaseline || !last) {
        storeTaskId(WEEKLY_TASK_KEY, taskId);
      } else if (last !== taskId) {
        storeTaskId(WEEKLY_TASK_KEY, taskId);
        showWeeklyRetroReadyNotice(retro, router);
      }
    }
  }
}

export function useDigestNotifier() {
  const authStore = useAuthStore();
  const router = useRouter();
  let timer: ReturnType<typeof setInterval> | null = null;

  const poll = async (initializeBaseline = false) => {
    if (!authStore.isAuthenticated) return;
    try {
      await syncDigestNotifications(router, { initializeBaseline });
    } catch {
      // 静默失败，避免打扰
    }
  };

  const start = () => {
    stop();
    void poll(true);
    timer = setInterval(() => {
      void poll(false);
    }, POLL_MS);
  };

  const stop = () => {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  };

  watch(
    () => authStore.isAuthenticated,
    (authenticated) => {
      if (authenticated) {
        start();
      } else {
        stop();
      }
    },
    { immediate: true },
  );

  onUnmounted(stop);
}
