<template>
  <div class="reminders-page noto-page">
    <a-card class="reminders-hero" :bordered="false">
      <div class="hero-row">
        <div>
          <h2 class="noto-page-title">提醒</h2>
        </div>
        <a-button type="primary" size="large" @click="openCreateModal">新建提醒</a-button>
      </div>
      <a-space wrap class="reminder-filters">
        <a-select
          v-model:value="filters.workspaceId"
          allow-clear
          placeholder="全部知识库"
          style="min-width: 160px"
          :options="workspaceOptions"
          @change="reload"
        />
        <a-select
          v-model:value="filters.status"
          allow-clear
          placeholder="全部状态"
          style="min-width: 140px"
          :options="statusOptions"
          @change="reload"
        />
      </a-space>
    </a-card>

    <a-card class="reminders-table-card" :bordered="false">
      <a-spin :spinning="loading">
        <ul v-if="isMobile" class="reminder-all-cards">
          <li v-for="record in reminders" :key="record.id" class="reminder-all-card">
            <div class="reminder-all-card-head">
              <a-tag :color="statusColor(record.status)">
                {{ REMINDER_STATUS_LABEL[record.status] || '未知' }}
              </a-tag>
              <span v-if="record.workspaceName" class="reminder-all-ws">{{ record.workspaceName }}</span>
            </div>
            <p class="reminder-all-card-title">{{ record.todoTitle || '待办' }}</p>
            <p class="reminder-all-card-meta">
              <span>{{ formatTime(record.triggerAt) }}</span>
            </p>
            <p v-if="record.message" class="reminder-all-card-msg">{{ record.message }}</p>
            <a-button
              v-if="record.noteId"
              type="link"
              size="small"
              class="reminder-all-note"
              @click="openNote(record)"
            >
              {{ record.noteTitle || '关联笔记' }}
            </a-button>
            <div class="reminder-all-card-actions">
              <a-button
                v-if="record.status === REMINDER_STATUS.PENDING"
                type="link"
                size="small"
                @click="openEditModal(record)"
              >
                编辑
              </a-button>
              <a-button
                v-if="record.status === REMINDER_STATUS.PENDING"
                type="link"
                size="small"
                @click="handleCancel(record.id)"
              >
                取消
              </a-button>
              <a-popconfirm title="确定删除该提醒？" ok-text="删除" cancel-text="取消" @confirm="removeReminder(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </div>
          </li>
          <EmptyState
            v-if="!loading && !reminders.length"
            title="暂无提醒"
            description="新建提醒或在待办中设置"
            preset="todo"
            compact
          />
        </ul>
        <div v-if="isMobile && total > 0" class="reminder-all-pagination">
          <a-pagination
            v-model:current="page"
            v-model:page-size="pageSize"
            :total="total"
            :show-size-changer="false"
            size="small"
            @change="onMobilePageChange"
          />
        </div>
      </a-spin>
      <a-table
        v-if="!isMobile"
        :columns="columns"
        :data-source="reminders"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'todo'">
            <div class="todo-cell">
              <strong>{{ record.todoTitle || '待办' }}</strong>
              <a-button
                v-if="record.noteId"
                type="link"
                size="small"
                class="note-link-btn"
                @click="openNote(record)"
              >
                {{ record.noteTitle || '关联笔记' }}
              </a-button>
            </div>
          </template>
          <template v-else-if="column.key === 'triggerAt'">
            {{ formatTime(record.triggerAt) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ REMINDER_STATUS_LABEL[record.status] || '未知' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button
                v-if="record.status === REMINDER_STATUS.PENDING"
                type="link"
                size="small"
                @click="openEditModal(record)"
              >
                编辑
              </a-button>
              <a-button
                v-if="record.status === REMINDER_STATUS.PENDING"
                type="link"
                size="small"
                @click="handleCancel(record.id)"
              >
                取消
              </a-button>
              <a-popconfirm title="确定删除该提醒？" ok-text="删除" cancel-text="取消" @confirm="removeReminder(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="modalMode === 'create' ? '新建提醒' : '编辑提醒'"
      :ok-text="modalMode === 'create' ? '创建' : '保存'"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="handleModalOk"
    >
      <a-form layout="vertical">
        <a-form-item label="知识库" required>
          <a-select
            v-model:value="form.workspaceId"
            placeholder="选择知识库"
            :options="workspaceOptions"
            :disabled="modalMode === 'edit'"
            @change="loadTodosForWorkspace"
          />
        </a-form-item>
        <a-form-item label="关联待办" required>
          <a-select
            v-model:value="form.todoId"
            placeholder="选择待办"
            :options="todoOptions"
            :disabled="!form.workspaceId || modalMode === 'edit'"
          />
        </a-form-item>
        <a-form-item label="触发时间" required>
          <a-date-picker
            v-model:value="form.triggerAt"
            show-time
            format="YYYY-MM-DD HH:mm"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="提醒内容">
          <a-textarea v-model:value="form.message" :rows="3" placeholder="可选，默认使用待办标题" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import dayjs, { type Dayjs } from 'dayjs';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  REMINDER_STATUS,
  REMINDER_STATUS_LABEL,
  cancelReminder,
  createReminder,
  deleteReminder,
  listReminders,
  updateReminder,
  type ReminderItem,
} from '../../api/reminders';
import { syncDueReminders } from '../../composables/useReminderNotifier';
import { listTodos, type TodoItem } from '../../api/todos';
import { listWorkspaces, type Workspace } from '../../api/workspaces';
import EmptyState from '../../components/common/EmptyState.vue';
import { useBreakpoint } from '../../composables/useBreakpoint';

const route = useRoute();
const router = useRouter();
const { isMobile } = useBreakpoint();

const loading = ref(false);
const saving = ref(false);
const modalOpen = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const editingId = ref('');
const reminders = ref<ReminderItem[]>([]);
const todos = ref<TodoItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const workspaces = ref<Workspace[]>([]);

const filters = reactive({
  workspaceId: undefined as string | undefined,
  status: undefined as number | undefined,
});

const form = reactive({
  workspaceId: undefined as string | undefined,
  todoId: undefined as string | undefined,
  triggerAt: null as Dayjs | null,
  message: '',
});

const statusOptions = Object.entries(REMINDER_STATUS_LABEL).map(([value, label]) => ({
  value: Number(value),
  label,
}));

const workspaceOptions = computed(() =>
  workspaces.value.map((item) => ({ label: item.name, value: item.id })),
);

const todoOptions = computed(() =>
  todos.value.map((item) => ({ label: item.title, value: item.id })),
);

const columns = [
  { title: '待办', key: 'todo', width: 240 },
  { title: '知识库', dataIndex: 'workspaceName', width: 140 },
  { title: '触发时间', key: 'triggerAt', width: 170 },
  { title: '提醒内容', dataIndex: 'message', ellipsis: true },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 180, fixed: 'right' as const },
];

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  onChange: (next: number, size: number) => {
    page.value = next;
    pageSize.value = size;
    loadReminders();
  },
}));

const onMobilePageChange = (next: number) => {
  page.value = next;
  void loadReminders();
};

const formatTime = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm');

const openNote = (item: ReminderItem) => {
  if (!item.noteId) return;
  const query: Record<string, string> = { from: 'reminders' };
  if (item.workspaceId) query.workspace = item.workspaceId;
  router.push({ path: `/notes/${item.noteId}`, query });
};

const statusColor = (status: number) => {
  if (status === REMINDER_STATUS.SENT) return 'success';
  if (status === REMINDER_STATUS.CANCELLED) return 'default';
  return 'processing';
};

const loadTodosForWorkspace = async () => {
  form.todoId = undefined;
  if (!form.workspaceId) {
    todos.value = [];
    return;
  }
  const data = await listTodos({ workspaceId: form.workspaceId, page: 1, size: 100 });
  todos.value = data.records || [];
};

const loadReminders = async () => {
  loading.value = true;
  try {
    await syncDueReminders({ notify: false });
    const data = await listReminders({
      page: page.value,
      size: pageSize.value,
      workspaceId: filters.workspaceId,
      status: filters.status,
      todoId: typeof route.query.todoId === 'string' ? route.query.todoId : undefined,
    });
    reminders.value = data.records || [];
    total.value = data.total || 0;
  } catch (error: any) {
    message.error(error?.message || '加载提醒失败');
  } finally {
    loading.value = false;
  }
};

const reload = () => {
  page.value = 1;
  loadReminders();
};

const resetForm = async () => {
  form.workspaceId = filters.workspaceId || workspaces.value[0]?.id;
  form.todoId = typeof route.query.todoId === 'string' ? route.query.todoId : undefined;
  form.triggerAt = dayjs().add(1, 'hour');
  form.message = typeof route.query.title === 'string' ? `待办提醒：${route.query.title}` : '';
  if (form.workspaceId) {
    await loadTodosForWorkspace();
  }
};

const openCreateModal = async () => {
  modalMode.value = 'create';
  editingId.value = '';
  await resetForm();
  modalOpen.value = true;
};

const openEditModal = (item: ReminderItem) => {
  modalMode.value = 'edit';
  editingId.value = item.id;
  form.workspaceId = item.workspaceId;
  form.todoId = item.todoId;
  form.triggerAt = dayjs(item.triggerAt);
  form.message = item.message || '';
  modalOpen.value = true;
};

const handleModalOk = async () => {
  if (!form.workspaceId || !form.todoId || !form.triggerAt) {
    message.warning('请完善必填项');
    return;
  }
  saving.value = true;
  try {
    const triggerAt = form.triggerAt.format('YYYY-MM-DDTHH:mm:ss');
    if (modalMode.value === 'create') {
      await createReminder({
        workspaceId: form.workspaceId,
        todoId: form.todoId,
        triggerAt,
        message: form.message.trim() || undefined,
        reminderType: 'once',
      });
      message.success('提醒已创建');
    } else {
      await updateReminder(editingId.value, {
        triggerAt,
        message: form.message.trim() || undefined,
        reminderType: 'once',
      });
      message.success('提醒已更新');
    }
    modalOpen.value = false;
    loadReminders();
  } catch (error: any) {
    message.error(error?.message || '保存失败');
  } finally {
    saving.value = false;
  }
};

const handleCancel = async (id: string) => {
  try {
    await cancelReminder(id);
    message.success('提醒已取消');
    loadReminders();
  } catch (error: any) {
    message.error(error?.message || '取消失败');
  }
};

const removeReminder = async (id: string) => {
  try {
    await deleteReminder(id);
    message.success('提醒已删除');
    loadReminders();
  } catch (error: any) {
    message.error(error?.message || '删除失败');
  }
};

const focusReminderFromQuery = async () => {
  const reminderId = typeof route.query.reminderId === 'string' ? route.query.reminderId : '';
  if (!reminderId) return;
  await loadReminders();
  const item = reminders.value.find((record) => record.id === reminderId);
  if (!item) {
    message.warning('未找到该提醒');
    return;
  }
  openEditModal(item);
  const nextQuery = { ...route.query };
  delete nextQuery.reminderId;
  router.replace({ path: '/reminders', query: nextQuery });
};

watch(
  () => route.query.reminderId,
  (reminderId) => {
    if (typeof reminderId === 'string' && reminderId) {
      void focusReminderFromQuery();
    }
  },
);

onMounted(async () => {
  workspaces.value = await listWorkspaces();
  if (typeof route.query.workspace === 'string') {
    filters.workspaceId = route.query.workspace;
  }
  if (route.query.create === '1') {
    await openCreateModal();
  } else if (typeof route.query.reminderId === 'string' && route.query.reminderId) {
    await focusReminderFromQuery();
  } else {
    loadReminders();
  }
});
</script>

<style scoped>
.reminders-hero,
.reminders-table-card {
  border-radius: var(--noto-radius-lg);
}

.hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.todo-cell {
  display: grid;
  gap: 4px;
}

.note-link-btn {
  padding: 0;
  height: auto;
  font-size: 12px;
}

.reminder-all-cards {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.reminder-all-card {
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--noto-border-soft, #eef2f7);
  background: var(--noto-surface-solid, #fff);
}

.reminder-all-card-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.reminder-all-ws {
  font-size: 12px;
  color: var(--noto-text-muted);
}

.reminder-all-card-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  word-break: break-word;
}

.reminder-all-card-meta {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--noto-accent-deep, #0891b2);
  font-weight: 500;
}

.reminder-all-card-msg {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--noto-text-muted);
  line-height: 1.5;
  word-break: break-word;
}

.reminder-all-note {
  padding: 0;
  height: auto;
  margin-bottom: 8px;
}

.reminder-all-card-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0 4px;
  padding-top: 8px;
  border-top: 1px solid var(--noto-border-soft, #eef2f7);
}

.reminder-all-pagination {
  display: flex;
  justify-content: center;
  padding: 16px 0 8px;
}
</style>
