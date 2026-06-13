import { watch } from 'vue';
import { useRouter } from 'vue-router';
import { h } from 'vue';
import { Button, notification } from 'ant-design-vue';
import { useAuthStore } from '../store/auth';
import { useTodoSummaryStore } from '../store/todoSummary';
import { showBrowserReminderNotification } from '../utils/browserNotification';

const STORAGE_KEY = 'noto-overdue-notified-date';

function todayKey() {
  return new Date().toISOString().slice(0, 10);
}

export function useOverdueNotifier() {
  const authStore = useAuthStore();
  const router = useRouter();
  const todoSummary = useTodoSummaryStore();

  const maybeNotify = async () => {
    if (!authStore.isAuthenticated) return;
    if (localStorage.getItem(STORAGE_KEY) === todayKey()) return;

    try {
      await todoSummary.refresh();
    } catch {
      return;
    }

    const count = todoSummary.overdueCount;
    if (count <= 0) return;

    localStorage.setItem(STORAGE_KEY, todayKey());
    const body = `你有 ${count} 项待办已逾期，建议优先处理`;
    showBrowserReminderNotification('逾期待办提醒', body);

    const key = `overdue-${todayKey()}`;
    notification.open({
      key,
      message: '逾期待办提醒',
      description: body,
      duration: 10,
      btn: () =>
        h(
          Button,
          {
            type: 'primary',
            size: 'small',
            onClick: () => {
              notification.close(key);
              router.push({ path: '/todos', query: { view: 'all', focus: 'overdue' } });
            },
          },
          () => '查看逾期',
        ),
    });
  };

  watch(
    () => authStore.isAuthenticated,
    (authenticated) => {
      if (authenticated) {
        void maybeNotify();
      }
    },
    { immediate: true },
  );
}
