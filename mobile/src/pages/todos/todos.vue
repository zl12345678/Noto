<template>
  <view class="page-safe">
    <view class="todo-toolbar card">
      <picker :range="workspaceOptions" :value="workspaceIndex" @change="onWorkspaceChange">
        <view class="toolbar-picker">{{ workspaceOptions[workspaceIndex] || '全部知识库' }} ▾</view>
      </picker>
      <view class="view-switch">
        <text
          v-for="option in viewOptions"
          :key="option.key"
          class="view-item"
          :class="{ active: viewMode === option.key }"
          @click="switchView(option.key)"
        >{{ option.label }}</text>
      </view>
    </view>

    <view v-if="viewMode === 'board'" class="segment">
      <text
        v-for="tab in tabs"
        :key="tab.key"
        class="segment-item"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >{{ tab.label }}</text>
    </view>

    <view class="top-actions">
      <TouchButton variant="primary" block @click="openCreate">+ 新建待办</TouchButton>
      <TouchButton block :loading="adviceLoading || icsExporting" @click="openPageActions">更多</TouchButton>
    </view>

    <view v-if="viewMode === 'all'" class="all-filters card">
      <input
        v-model="tableFilters.keyword"
        class="filter-input"
        placeholder="搜索待办"
        confirm-type="search"
        @confirm="loadTodoList"
      />
      <view class="filter-row">
        <picker :range="quickFilterOptions" range-key="label" :value="quickFilterIndex" @change="onQuickFilterChange">
          <view class="filter-chip">{{ quickFilterOptions[quickFilterIndex]?.label }}</view>
        </picker>
        <picker :range="horizonOptions" range-key="label" :value="horizonIndex" @change="onHorizonChange">
          <view class="filter-chip">{{ horizonOptions[horizonIndex]?.label }}</view>
        </picker>
        <picker :range="statusOptions" range-key="label" :value="statusIndex" @change="onStatusChange">
          <view class="filter-chip">{{ statusOptions[statusIndex]?.label }}</view>
        </picker>
      </view>
    </view>

    <view v-if="overdueAdvice && viewMode === 'board'" class="card advice-card">
      <view class="advice-head">
        <view>
          <text class="advice-label">AI 逾期建议</text>
          <text class="advice-summary">{{ overdueAdvice.summary }}</text>
        </view>
        <text class="link" @click="overdueAdvice = null">收起</text>
      </view>
      <view v-if="!overdueAdvice.suggestions.length" class="muted advice-empty">没有需要处理的逾期待办</view>
      <view v-for="suggestion in overdueAdvice.suggestions" :key="String(suggestion.todoId)" class="advice-item">
        <view class="advice-main">
          <text class="advice-title">{{ suggestion.title }}</text>
          <text class="advice-reason">{{ overdueActionLabel(suggestion.action) }} · {{ suggestion.reason }}</text>
          <text v-if="suggestion.suggestedDueAt" class="muted">建议改到 {{ formatDateTime(suggestion.suggestedDueAt) }}</text>
        </view>
        <TouchButton
          :loading="applyingAdviceId === String(suggestion.todoId)"
          @click="applyOverdueSuggestion(suggestion)"
        >应用</TouchButton>
      </view>
    </view>

    <template v-if="viewMode === 'board'">
      <view v-if="loading" class="empty">加载中…</view>
      <view v-else-if="!currentList.length" class="empty card">{{ emptyText }}</view>

      <view v-for="item in currentList" :key="item.id" class="card todo-card">
        <view class="todo-head">
          <text class="todo-title">{{ item.title }}</text>
          <text class="priority" :class="`p-${item.priority || 2}`">{{ priorityLabel(item.priority) }}</text>
        </view>
        <text v-if="item.description" class="todo-desc">{{ item.description }}</text>
        <view class="meta-row">
          <text v-if="item.noteTitle" class="muted">{{ item.noteTitle }}</text>
          <text class="muted">{{ horizonLabel(item.horizon) }}</text>
          <text class="muted">{{ statusLabel(item.status) }}</text>
        </view>
        <text v-if="item.dueAt" class="due" :class="{ overdue: isTodoOverdue(item) }">
          截止 {{ formatDateTime(item.dueAt) }}
        </text>
        <view class="action-row">
          <TouchButton
            v-if="item.status === TODO_STATUS.IN_PROGRESS"
            block
            :loading="retroCompletingId === item.id"
            @click="completeWithRetro(item)"
          >完成并复盘</TouchButton>
          <TouchButton
            v-else-if="primaryActionFor(item)"
            :variant="primaryActionFor(item)?.primary ? 'primary' : 'ghost'"
            block
            @click="primaryActionFor(item)?.handler(item.id)"
          >{{ primaryActionFor(item)?.label }}</TouchButton>
          <TouchButton block :loading="aiBreakingId === item.id" @click="openTodoActions(item)">更多</TouchButton>
        </view>
      </view>
    </template>

    <template v-else>
      <view v-if="listLoading" class="empty">加载中…</view>
      <view v-else-if="!displayedTodos.length" class="empty card">没有匹配的待办</view>
      <view v-for="item in displayedTodos" :key="item.id" class="card todo-card all-todo-card">
        <view class="todo-head">
          <text class="todo-title">{{ item.title }}</text>
          <text class="priority" :class="`p-${item.priority || 2}`">{{ priorityLabel(item.priority) }}</text>
        </view>
        <text v-if="item.description" class="todo-desc">{{ item.description }}</text>
        <view class="meta-row">
          <text v-if="item.workspaceName" class="muted">{{ item.workspaceName }}</text>
          <text class="muted">{{ horizonLabel(item.horizon) }}</text>
          <text class="muted">{{ statusLabel(item.status) }}</text>
        </view>
        <text v-if="item.dueAt" class="due" :class="{ overdue: isTodoOverdue(item) }">
          截止 {{ formatDateTime(item.dueAt) }}
        </text>
        <view class="action-row compact">
          <TouchButton
            v-if="primaryActionForTodo(item)"
            :variant="primaryActionForTodo(item)?.primary ? 'primary' : 'ghost'"
            block
            @click="primaryActionForTodo(item)?.handler(item.id)"
          >{{ primaryActionForTodo(item)?.label }}</TouchButton>
          <TouchButton block :loading="aiBreakingId === item.id" @click="openTodoActions(item)">更多</TouchButton>
        </view>
      </view>
    </template>

    <TodoSheet
      :open="todoSheetOpen"
      :mode="todoSheetMode"
      :workspaces="workspaceState.items"
      :initial-todo="editingTodo"
      :default-workspace-id="workspaceState.items[0]?.id || null"
      @close="closeTodoSheet"
      @saved="onTodoSaved"
    />

    <ReminderSheet
      :open="reminderSheetOpen"
      :workspaces="workspaceState.items"
      :initial-todo="reminderTodo"
      :default-workspace-id="reminderTodo?.workspaceId || workspaceState.items[0]?.id || null"
      @close="closeReminder"
      @saved="onReminderSaved"
    />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import ReminderSheet from '../../components/ReminderSheet.vue';
import TouchButton from '../../components/TouchButton.vue';
import TodoSheet from '../../components/TodoSheet.vue';
import {
  deleteTodo,
  exportTodosIcs,
  getTodoBoard,
  isTodoOverdue,
  listTodos,
  patchTodoStatus,
  TODO_HORIZON_LABEL,
  TODO_PRIORITY_LABEL,
  TODO_STATUS,
  TODO_STATUS_LABEL,
  createTodo,
  updateTodo,
  type TodoBoard,
  type TodoHorizonType,
  type TodoItem,
} from '../../api/todos';
import {
  breakdownTodoByAi,
  createTodoCompletionRetro,
  getTodoOverdueAdvice,
  type AiSubtaskSuggestion,
  type TodoOverdueAdvice,
  type TodoOverdueAdviceItem,
} from '../../api/ai';
import { ensureAuthPage } from '../../stores/auth';
import { refreshWorkspaces, workspaceState } from '../../stores/workspace';
import { formatDateTime } from '../../utils/format';

type TabKey = 'queue' | 'active' | 'long';
type ViewMode = 'board' | 'all';
type QuickFilter = 'all' | 'overdue' | 'due_today' | 'no_due';

const tabs = [
  { key: 'queue' as TabKey, label: '队列' },
  { key: 'active' as TabKey, label: '进行中' },
  { key: 'long' as TabKey, label: '长期' },
];
const viewOptions = [
  { key: 'board' as ViewMode, label: '行动看板' },
  { key: 'all' as ViewMode, label: '全部列表' },
];
const quickFilterOptions = [
  { label: '全部', value: 'all' as QuickFilter },
  { label: '逾期', value: 'overdue' as QuickFilter },
  { label: '今日到期', value: 'due_today' as QuickFilter },
  { label: '无截止', value: 'no_due' as QuickFilter },
];
const horizonOptions = [
  { label: '全部类型', value: '' },
  ...Object.entries(TODO_HORIZON_LABEL).map(([value, label]) => ({ label, value })),
];
const statusOptions = [
  { label: '全部状态', value: '' as number | '' },
  ...Object.entries(TODO_STATUS_LABEL).map(([value, label]) => ({ label, value: Number(value) as number | '' })),
];

const viewMode = ref<ViewMode>('board');
const activeTab = ref<TabKey>('queue');
const board = ref<TodoBoard | null>(null);
const todos = ref<TodoItem[]>([]);
const loading = ref(false);
const listLoading = ref(false);
const todoSheetOpen = ref(false);
const todoSheetMode = ref<'create' | 'edit'>('create');
const editingTodo = ref<TodoItem | null>(null);
const reminderSheetOpen = ref(false);
const reminderTodo = ref<TodoItem | null>(null);
const aiBreakingId = ref('');
const adviceLoading = ref(false);
const applyingAdviceId = ref('');
const overdueAdvice = ref<TodoOverdueAdvice | null>(null);
const retroCompletingId = ref('');
const icsExporting = ref(false);
const selectedWorkspaceId = ref('');
const quickFilter = ref<QuickFilter>('all');
const tableFilters = ref<{
  keyword: string;
  horizon: TodoHorizonType | '';
  status: number | '';
}>({
  keyword: '',
  horizon: '',
  status: '',
});

const workspaceOptions = computed(() => ['全部知识库', ...workspaceState.items.map((item) => item.name)]);
const workspaceIndex = computed(() => {
  if (!selectedWorkspaceId.value) return 0;
  const index = workspaceState.items.findIndex((item) => String(item.id) === selectedWorkspaceId.value);
  return index >= 0 ? index + 1 : 0;
});
const quickFilterIndex = computed(() => Math.max(0, quickFilterOptions.findIndex((item) => item.value === quickFilter.value)));
const horizonIndex = computed(() => Math.max(0, horizonOptions.findIndex((item) => item.value === tableFilters.value.horizon)));
const statusIndex = computed(() => Math.max(0, statusOptions.findIndex((item) => item.value === tableFilters.value.status)));

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

const allTodos = computed(() => {
  if (!board.value) return [] as TodoItem[];
  return [...board.value.actionTodos, ...board.value.parallelTodos, ...board.value.longTermTodos];
});

const displayedTodos = computed(() => {
  if (quickFilter.value === 'all') return todos.value;
  return todos.value.filter((item) => {
    if (item.status === TODO_STATUS.COMPLETED || item.status === TODO_STATUS.CANCELLED) return false;
    if (quickFilter.value === 'overdue') return isTodoOverdue(item);
    if (quickFilter.value === 'due_today') {
      if (!item.dueAt) return false;
      const due = new Date(item.dueAt);
      const now = new Date();
      return due.getFullYear() === now.getFullYear() &&
        due.getMonth() === now.getMonth() &&
        due.getDate() === now.getDate();
    }
    if (quickFilter.value === 'no_due') return !item.dueAt;
    return true;
  });
});

function statusLabel(status: number) {
  return TODO_STATUS_LABEL[status] || '未知';
}

function horizonLabel(horizon: TodoHorizonType) {
  return TODO_HORIZON_LABEL[horizon] || '近期行动';
}

function priorityLabel(priority?: number | null) {
  return TODO_PRIORITY_LABEL[priority || 2] || '中';
}

function actionsFor(item: TodoItem) {
  if (activeTab.value === 'queue') {
    return [{ label: '开始执行', primary: true, handler: (id: string) => setStatus(id, TODO_STATUS.IN_PROGRESS) }];
  }
  if (activeTab.value === 'active') {
    return [{ label: '退回队列', primary: false, handler: (id: string) => setStatus(id, TODO_STATUS.PENDING) }];
  }
  if (item.status === TODO_STATUS.PENDING) {
    return [{ label: '开始', primary: true, handler: (id: string) => setStatus(id, TODO_STATUS.IN_PROGRESS) }];
  }
  return [];
}

function actionsForTodo(item: TodoItem) {
  if (item.status === TODO_STATUS.PENDING) {
    return [{ label: '开始执行', primary: true, handler: (id: string) => setStatus(id, TODO_STATUS.IN_PROGRESS) }];
  }
  if (item.status === TODO_STATUS.IN_PROGRESS) {
    return [
      { label: '退回队列', primary: false, handler: (id: string) => setStatus(id, TODO_STATUS.PENDING) },
      { label: '完成', primary: true, handler: (id: string) => setStatus(id, TODO_STATUS.COMPLETED) },
    ];
  }
  return [];
}

function primaryActionFor(item: TodoItem) {
  return actionsFor(item)[0] || null;
}

function primaryActionForTodo(item: TodoItem) {
  return actionsForTodo(item)[0] || null;
}

function openPageActions() {
  const items = viewMode.value === 'board' ? ['AI 逾期建议', '导出日历'] : ['导出日历'];
  uni.showActionSheet({
    itemList: items,
    success: async (res) => {
      const label = items[res.tapIndex];
      if (label === 'AI 逾期建议') await loadOverdueAdvice();
      if (label === '导出日历') await handleExportIcs();
    },
  });
}

function openTodoActions(item: TodoItem) {
  const items = ['编辑', '设提醒', 'AI 拆解'];
  if (item.noteId) items.push('打开笔记');
  actionsFor(item).forEach((action) => {
    if (!items.includes(action.label)) items.push(action.label);
  });
  actionsForTodo(item).forEach((action) => {
    if (!items.includes(action.label)) items.push(action.label);
  });
  items.push('删除');
  uni.showActionSheet({
    itemList: items,
    success: async (res) => {
      const label = items[res.tapIndex];
      if (label === '编辑') openEdit(item);
      if (label === '设提醒') openReminder(item);
      if (label === 'AI 拆解') await runBreakdown(item);
      if (label === '打开笔记') openLinkedNote(item);
      if (label === '删除') onDelete(item);
      const action = [...actionsFor(item), ...actionsForTodo(item)].find((entry) => entry.label === label);
      if (action) action.handler(item.id);
    },
  });
}

function openLinkedNote(item: TodoItem) {
  if (!item.noteId) return;
  uni.navigateTo({ url: `/pages/notes/detail?id=${item.noteId}` });
}

function overdueActionLabel(action: string) {
  const labels: Record<string, string> = {
    reschedule: '改期',
    breakdown: '拆解',
    archive: '归档',
    complete: '完成',
  };
  return labels[action] || '处理';
}

async function load() {
  loading.value = true;
  try {
    board.value = await getTodoBoard(selectedWorkspaceId.value || undefined);
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

async function loadTodoList() {
  listLoading.value = true;
  try {
    const data = await listTodos({
      page: 1,
      size: 200,
      workspaceId: selectedWorkspaceId.value || null,
      keyword: tableFilters.value.keyword.trim() || undefined,
      horizon: tableFilters.value.horizon || null,
      status: tableFilters.value.status === '' ? null : tableFilters.value.status,
    });
    todos.value = data.records || [];
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    listLoading.value = false;
  }
}

async function refreshCurrentView() {
  if (viewMode.value === 'board') await load();
  else await loadTodoList();
}

function switchView(mode: ViewMode) {
  if (viewMode.value === mode) return;
  viewMode.value = mode;
  refreshCurrentView();
}

function onWorkspaceChange(e: { detail: { value: string | number } }) {
  const index = Number(e.detail.value);
  selectedWorkspaceId.value = index > 0 ? String(workspaceState.items[index - 1]?.id || '') : '';
  overdueAdvice.value = null;
  refreshCurrentView();
}

function onQuickFilterChange(e: { detail: { value: string | number } }) {
  quickFilter.value = quickFilterOptions[Number(e.detail.value)]?.value || 'all';
}

function onHorizonChange(e: { detail: { value: string | number } }) {
  tableFilters.value.horizon = horizonOptions[Number(e.detail.value)]?.value as TodoHorizonType | '';
  loadTodoList();
}

function onStatusChange(e: { detail: { value: string | number } }) {
  tableFilters.value.status = statusOptions[Number(e.detail.value)]?.value as number | '';
  loadTodoList();
}

async function setStatus(id: string, status: number) {
  try {
    await patchTodoStatus(id, status);
    await refreshCurrentView();
  } catch (e: any) {
    uni.showToast({ title: e?.message || '操作失败', icon: 'none' });
  }
}

async function loadOverdueAdvice() {
  adviceLoading.value = true;
  try {
    overdueAdvice.value = await getTodoOverdueAdvice(selectedWorkspaceId.value || undefined);
  } catch (e: any) {
    uni.showToast({ title: e?.message || 'AI 建议生成失败', icon: 'none' });
  } finally {
    adviceLoading.value = false;
  }
}

async function handleExportIcs() {
  icsExporting.value = true;
  try {
    await exportTodosIcs(selectedWorkspaceId.value || undefined);
    uni.showToast({ title: 'ICS 已导出', icon: 'success' });
  } catch (e: any) {
    const message = e?.message || '导出失败';
    if (message.includes('已下载')) {
      uni.showModal({ title: '导出完成', content: message, showCancel: false });
    } else {
      uni.showToast({ title: message, icon: 'none' });
    }
  } finally {
    icsExporting.value = false;
  }
}

function findTodo(id: string | number) {
  return allTodos.value.find((item) => item.id === String(id)) || null;
}

async function applyOverdueSuggestion(suggestion: TodoOverdueAdviceItem) {
  const id = String(suggestion.todoId);
  const todo = findTodo(id);
  if (!todo) {
    uni.showToast({ title: '请先刷新待办列表', icon: 'none' });
    return;
  }
  applyingAdviceId.value = id;
  try {
    if (suggestion.action === 'complete') {
      await patchTodoStatus(id, TODO_STATUS.COMPLETED);
    } else if (suggestion.action === 'archive') {
      await patchTodoStatus(id, TODO_STATUS.CANCELLED);
    } else if (suggestion.action === 'breakdown') {
      await runBreakdown(todo);
    } else if (suggestion.action === 'reschedule' && suggestion.suggestedDueAt) {
      await updateTodo(id, {
        noteId: todo.noteId || null,
        title: todo.title,
        description: todo.description || undefined,
        priority: todo.priority,
        horizon: todo.horizon,
        dueAt: suggestion.suggestedDueAt,
      });
    }
    uni.showToast({ title: '已应用建议', icon: 'success' });
    await refreshCurrentView();
    await loadOverdueAdvice();
  } catch (e: any) {
    uni.showToast({ title: e?.message || '应用失败', icon: 'none' });
  } finally {
    applyingAdviceId.value = '';
  }
}

function openCreate() {
  if (!workspaceState.items.length) {
    uni.showToast({ title: '暂无知识库', icon: 'none' });
    return;
  }
  todoSheetMode.value = 'create';
  editingTodo.value = null;
  todoSheetOpen.value = true;
}

function openEdit(item: TodoItem) {
  todoSheetMode.value = 'edit';
  editingTodo.value = item;
  todoSheetOpen.value = true;
}

function closeTodoSheet() {
  todoSheetOpen.value = false;
  editingTodo.value = null;
}

async function onTodoSaved() {
  closeTodoSheet();
  activeTab.value = todoSheetMode.value === 'create' ? 'queue' : activeTab.value;
  await refreshCurrentView();
}

function onDelete(item: TodoItem) {
  uni.showModal({
    title: '删除待办',
    content: `确定删除「${item.title}」吗？相关提醒也可能失去关联。`,
    confirmColor: '#b91c1c',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await deleteTodo(item.id);
        uni.showToast({ title: '已删除', icon: 'none' });
        await refreshCurrentView();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '删除失败', icon: 'none' });
      }
    },
  });
}

function openReminder(item: TodoItem) {
  reminderTodo.value = item;
  reminderSheetOpen.value = true;
}

async function runBreakdown(item: TodoItem) {
  aiBreakingId.value = item.id;
  try {
    const result = await breakdownTodoByAi({
      title: item.title,
      description: item.description || undefined,
      horizon: item.horizon,
    });
    const subtasks = result.subtasks || [];
    if (!subtasks.length) {
      uni.showToast({ title: 'AI 未生成子任务', icon: 'none' });
      return;
    }
    confirmCreateSubtasks(item, subtasks);
  } catch (e: any) {
    uni.showToast({ title: e?.message || 'AI 拆解失败', icon: 'none' });
  } finally {
    aiBreakingId.value = '';
  }
}

function confirmCreateSubtasks(parent: TodoItem, subtasks: AiSubtaskSuggestion[]) {
  const picked = subtasks.slice(0, 8);
  const content = picked.map((item, index) => `${index + 1}. ${item.title}`).join('\n');
  uni.showModal({
    title: `拆解为 ${picked.length} 条子任务`,
    content: `${content}\n\n确认后会创建这些子任务，并归档原待办。`,
    confirmText: '创建',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await Promise.all(picked.map((item) => createTodo({
          workspaceId: parent.workspaceId,
          noteId: parent.noteId || null,
          title: item.title,
          priority: item.priority,
          horizon: item.horizon || 'action',
          dueAt: item.dueAt || null,
        })));
        await patchTodoStatus(parent.id, TODO_STATUS.CANCELLED);
        uni.showToast({ title: `已创建 ${picked.length} 条`, icon: 'success' });
        activeTab.value = 'queue';
        await refreshCurrentView();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '创建子任务失败', icon: 'none' });
      }
    },
  });
}

async function completeWithRetro(item: TodoItem) {
  retroCompletingId.value = item.id;
  try {
    await patchTodoStatus(item.id, TODO_STATUS.COMPLETED);
    try {
      const retro = await createTodoCompletionRetro(item.id, {
        append: Boolean(item.noteId),
        scenario: 'completed',
      });
      const title = retro.appended ? '已完成并写入复盘' : '已完成，复盘如下';
      uni.showModal({
        title,
        content: retro.line || '已完成',
        confirmText: retro.noteId ? '查看笔记' : '知道了',
        cancelText: '关闭',
        success: (res) => {
          if (res.confirm && retro.noteId) {
            uni.navigateTo({ url: `/pages/notes/detail?id=${retro.noteId}` });
          }
        },
      });
    } catch (e: any) {
      uni.showToast({ title: e?.message || '已完成，复盘生成失败', icon: 'none' });
    }
    await refreshCurrentView();
  } catch (e: any) {
    uni.showToast({ title: e?.message || '完成失败', icon: 'none' });
  } finally {
    retroCompletingId.value = '';
  }
}

function closeReminder() {
  reminderSheetOpen.value = false;
  reminderTodo.value = null;
}

async function onReminderSaved() {
  closeReminder();
  await refreshCurrentView();
}

onShow(async () => {
  if (!ensureAuthPage()) return;
  await refreshWorkspaces();
  await refreshCurrentView();
});

onPullDownRefresh(async () => {
  await refreshCurrentView();
  uni.stopPullDownRefresh();
});
</script>

<style scoped lang="scss">
.todo-toolbar {
  padding: 22rpx;
  margin-bottom: 16rpx;
}

.toolbar-picker {
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 22rpx;
  border-radius: 16rpx;
  background: #fafaf9;
  color: #292524;
  font-size: 27rpx;
  font-weight: 650;
}

.view-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx;
  margin-top: 14rpx;
  padding: 8rpx;
  border-radius: 18rpx;
  background: #f5f5f4;
}

.view-item {
  height: 58rpx;
  line-height: 58rpx;
  border-radius: 14rpx;
  color: #78716c;
  text-align: center;
  font-size: 25rpx;
  font-weight: 650;
}

.view-item.active {
  background: #fff;
  color: #0e7490;
  box-shadow: 0 8rpx 18rpx rgba(68, 64, 60, 0.06);
}

.segment { margin-top: 0; }

.top-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190rpx, 1fr));
  gap: 12rpx;
  margin-top: 16rpx;
}

.all-filters {
  padding: 22rpx;
  margin-top: 18rpx;
}

.filter-input {
  height: 76rpx;
  padding: 0 22rpx;
  border-radius: 16rpx;
  background: #fafaf9;
  border: 1rpx solid #e7e5e4;
  color: #292524;
  font-size: 27rpx;
  box-sizing: border-box;
}

.filter-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10rpx;
  margin-top: 14rpx;
}

.filter-chip {
  height: 62rpx;
  line-height: 62rpx;
  padding: 0 12rpx;
  border-radius: 14rpx;
  background: #ecfeff;
  color: #0e7490;
  text-align: center;
  font-size: 23rpx;
  font-weight: 650;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.advice-card {
  margin-top: 20rpx;
  padding: 24rpx;
}

.advice-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.advice-label {
  display: block;
  color: #0e7490;
  font-size: 24rpx;
  font-weight: 600;
}

.advice-summary {
  display: block;
  margin-top: 6rpx;
  color: #1c1917;
  font-size: 28rpx;
  line-height: 1.5;
}

.advice-empty {
  display: block;
  padding-top: 8rpx;
}

.advice-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 0;
  border-top: 1rpx solid #f5f5f4;
}

.advice-main {
  flex: 1;
  min-width: 0;
}

.advice-title,
.advice-reason {
  display: block;
  line-height: 1.45;
}

.advice-title {
  color: #1c1917;
  font-size: 28rpx;
  font-weight: 600;
}

.advice-reason {
  margin-top: 6rpx;
  color: #57534e;
  font-size: 24rpx;
}

.todo-card { margin-top: 20rpx; }

.todo-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 8rpx;
}

.todo-title {
  flex: 1;
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  line-height: 1.4;
}

.priority {
  flex: 0 0 auto;
  min-width: 52rpx;
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: #f5f5f4;
  color: #57534e;
  font-size: 22rpx;
  text-align: center;
}

.priority.p-3 {
  background: #fff7ed;
  color: #c2410c;
}

.priority.p-1 {
  background: #f0fdf4;
  color: #15803d;
}

.todo-desc {
  display: block;
  margin: 8rpx 0 10rpx;
  color: #57534e;
  font-size: 26rpx;
  line-height: 1.5;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 8rpx;
}

.due {
  display: block;
  margin-top: 10rpx;
  color: #0e7490;
  font-size: 24rpx;
}

.due.overdue {
  color: #b91c1c;
}

.action-row {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 20rpx;
}

.action-row.compact {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.all-todo-card {
  border-left: 6rpx solid #bae6fd;
}
</style>
