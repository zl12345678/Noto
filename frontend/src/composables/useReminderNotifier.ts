import { onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { h } from 'vue';
import { Button, notification } from 'ant-design-vue';
import { fetchDueReminders, type ReminderItem } from '../api/reminders';
import { patchTodoStatus, TODO_STATUS } from '../api/todos';
import { useAuthStore } from '../store/auth';
import { useTodoSummaryStore } from '../store/todoSummary';
import { offerCompletionRetro, quickWriteCompletionRetro } from '../utils/reminderRetro';
import { showBrowserReminderNotification } from '../utils/browserNotification';

const POLL_MS = 30000;
const notifiedIds = new Set<string>();

function buildNoteQuery(item: ReminderItem) {
  const query: Record<string, string> = { from: 'reminders' };
  if (item.workspaceId) query.workspace = item.workspaceId;
  return query;
}

async function completeReminderTodo(item: ReminderItem) {
  try {
    await patchTodoStatus(item.todoId, TODO_STATUS.COMPLETED);
    notification.success({
      message: '已完成',
      description: `「${item.todoTitle || '待办'}」已标记完成`,
      duration: 3,
    });
    useTodoSummaryStore().refresh();
    await offerCompletionRetro(item);
  } catch (error: any) {
    notification.error({
      message: '操作失败',
      description: error?.message || '无法标记完成',
      duration: 4,
    });
  }
}

function renderReminderDescription(item: ReminderItem) {
  const children = [
    h('p', { style: 'margin:0 0 8px' }, item.message || item.todoTitle || '您有一条待办需要处理'),
  ];
  if (item.noteContext) {
    children.push(
      h(
        'div',
        {
          style:
            'margin-top:8px;padding:8px 10px;border-radius:8px;background:#f5f8ff;font-size:12px;color:#475467;line-height:1.5',
        },
        [
          h('strong', { style: 'color:#1677ff' }, item.noteTitle || '关联笔记'),
          h('span', ' · '),
          h('span', item.noteContext),
        ],
      ),
    );
  }
  return h('div', children);
}

function showReminderNotifications(items: ReminderItem[], router: ReturnType<typeof useRouter>) {
  for (const item of items) {
    if (notifiedIds.has(item.id)) continue;
    notifiedIds.add(item.id);
    const key = `reminder-${item.id}`;
    const bodyText = item.message || item.todoTitle || '您有一条待办需要处理';
    showBrowserReminderNotification('待办到期了', bodyText);
    notification.open({
      key,
      message: '待办到期了',
      description: () => renderReminderDescription(item),
      duration: 16,
      btn: () => {
        const buttons = [
          h(
            Button,
            {
              type: 'primary',
              size: 'small',
              onClick: () => {
                notification.close(key);
                void completeReminderTodo(item);
              },
            },
            () => '标记完成',
          ),
          h(
            Button,
            {
              size: 'small',
              onClick: () => {
                notification.close(key);
                router.push({ path: '/todos', query: { view: 'board' } });
              },
            },
            () => '去处理',
          ),
        ];
        if (item.noteId) {
          buttons.unshift(
            h(
              Button,
              {
                size: 'small',
                onClick: () => {
                  notification.close(key);
                  void quickWriteCompletionRetro(item);
                },
              },
              () => '一键复盘',
            ),
          );
          buttons.push(
            h(
              Button,
              {
                size: 'small',
                onClick: () => {
                  notification.close(key);
                  router.push({ path: `/notes/${item.noteId}`, query: buildNoteQuery(item) });
                },
              },
              () => '打开文档',
            ),
          );
        }
        return h('div', { style: 'display:flex;flex-wrap:wrap;gap:8px;margin-top:8px' }, buttons);
      },
    });
  }
}

export async function syncDueReminders(options?: { notify?: boolean }) {
  const items = await fetchDueReminders();
  if (options?.notify === false) {
    items.forEach((item) => notifiedIds.add(item.id));
    return items;
  }
  return items;
}

export function useReminderNotifier() {
  const authStore = useAuthStore();
  const router = useRouter();
  let timer: ReturnType<typeof setInterval> | null = null;

  const poll = async () => {
    if (!authStore.isAuthenticated) return;
    try {
      const items = await fetchDueReminders();
      showReminderNotifications(items, router);
    } catch {
      // 轮询失败时静默，避免打扰用户
    }
  };

  const start = () => {
    stop();
    void poll();
    timer = setInterval(poll, POLL_MS);
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
        notifiedIds.clear();
      }
    },
    { immediate: true },
  );

  onUnmounted(stop);
}
