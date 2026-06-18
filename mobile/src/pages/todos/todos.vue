<template>
  <view class="page-safe">
    <view class="segment">
      <text
        v-for="tab in tabs"
        :key="tab.key"
        class="segment-item"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >{{ tab.label }}</text>
    </view>

    <TouchButton variant="primary" block @click="openCreate">+ 新建待办</TouchButton>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!currentList.length" class="empty card">{{ emptyText }}</view>

    <view v-for="item in currentList" :key="item.id" class="card todo-card">
      <text class="todo-title">{{ item.title }}</text>
      <text v-if="item.noteTitle" class="muted">{{ item.noteTitle }}</text>
      <text v-if="activeTab === 'long'" class="muted">{{ statusLabel(item.status) }}</text>
      <view class="action-row">
        <TouchButton
          v-for="action in actionsFor(item)"
          :key="action.label"
          :variant="action.primary ? 'primary' : 'ghost'"
          block
          @click="action.handler(item.id)"
        >{{ action.label }}</TouchButton>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import TouchButton from '../../components/TouchButton.vue';
import {
  createTodo,
  getTodoBoard,
  patchTodoStatus,
  TODO_STATUS,
  TODO_STATUS_LABEL,
  type TodoBoard,
  type TodoItem,
} from '../../api/todos';
import { ensureAuthPage } from '../../stores/auth';
import { refreshWorkspaces, workspaceState } from '../../stores/workspace';

type TabKey = 'queue' | 'active' | 'long';

const tabs = [
  { key: 'queue' as TabKey, label: '队列' },
  { key: 'active' as TabKey, label: '进行中' },
  { key: 'long' as TabKey, label: '长期' },
];

const activeTab = ref<TabKey>('queue');
const board = ref<TodoBoard | null>(null);
const loading = ref(false);

const currentList = computed(() => {
  if (!board.value) return [] as TodoItem[];
  if (activeTab.value === 'queue') return board.value.actionTodos;
  if (activeTab.value === 'active') return board.value.parallelTodos;
  return board.value.longTermTodos;
});

const emptyText = computed(() => {
  if (activeTab.value === 'queue') return '队列为空';
  if (activeTab.value === 'active') return '暂无进行中任务';
  return '暂无长期目标';
});

function statusLabel(status: number) {
  return TODO_STATUS_LABEL[status] || '未知';
}

function actionsFor(item: TodoItem) {
  if (activeTab.value === 'queue') {
    return [{ label: '开始执行', primary: true, handler: (id: string) => setStatus(id, TODO_STATUS.IN_PROGRESS) }];
  }
  if (activeTab.value === 'active') {
    return [
      { label: '完成', primary: true, handler: (id: string) => setStatus(id, TODO_STATUS.COMPLETED) },
      { label: '退回队列', primary: false, handler: (id: string) => setStatus(id, TODO_STATUS.PENDING) },
    ];
  }
  if (item.status === TODO_STATUS.PENDING) {
    return [{ label: '开始', primary: true, handler: (id: string) => setStatus(id, TODO_STATUS.IN_PROGRESS) }];
  }
  return [];
}

async function load() {
  loading.value = true;
  try {
    board.value = await getTodoBoard();
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

async function setStatus(id: string, status: number) {
  await patchTodoStatus(id, status);
  await load();
}

function openCreate() {
  const ws = workspaceState.items[0];
  if (!ws) {
    uni.showToast({ title: '暂无知识库', icon: 'none' });
    return;
  }
  uni.showModal({
    title: '新建待办',
    editable: true,
    placeholderText: '待办标题',
    success: async (res) => {
      if (res.confirm && res.content?.trim()) {
        await createTodo({ workspaceId: String(ws.id), title: res.content.trim() });
        activeTab.value = 'queue';
        await load();
      }
    },
  });
}

onShow(async () => {
  if (!ensureAuthPage()) return;
  await refreshWorkspaces();
  load();
});

onPullDownRefresh(async () => {
  await load();
  uni.stopPullDownRefresh();
});
</script>

<style scoped lang="scss">
.segment { margin-top: 0; }

.todo-card { margin-top: 20rpx; }

.todo-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}

.action-row {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 20rpx;
}
</style>
