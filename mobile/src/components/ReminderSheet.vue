<template>
  <view v-if="open" class="sheet-mask" @click="handleClose">
    <view class="sheet" @click.stop>
      <view class="sheet-handle" />
      <view class="sheet-head">
        <view>
          <text class="sheet-title">{{ mode === 'edit' ? '编辑提醒' : '设置提醒' }}</text>
          <text class="sheet-subtitle">{{ selectedTodo?.title || '选择一个待办并设置触发时间' }}</text>
        </view>
        <text class="sheet-close" @click="handleClose">关闭</text>
      </view>

      <view class="form-group">
        <text class="label">知识库</text>
        <picker
          :disabled="locked || mode === 'edit'"
          :range="workspaceNames"
          :value="workspaceIndex"
          @change="onWorkspaceChange"
        >
          <view class="picker-field" :class="{ disabled: locked || mode === 'edit' }">
            <text>{{ selectedWorkspaceName || '请选择知识库' }}</text>
            <text class="picker-arrow">›</text>
          </view>
        </picker>
      </view>

      <view class="form-group">
        <text class="label">关联待办</text>
        <picker
          :disabled="locked || mode === 'edit' || !todos.length"
          :range="todoNames"
          :value="todoIndex"
          @change="onTodoChange"
        >
          <view class="picker-field" :class="{ disabled: locked || mode === 'edit' || !todos.length }">
            <text>{{ selectedTodo?.title || (todosLoading ? '加载待办…' : '请选择待办') }}</text>
            <text class="picker-arrow">›</text>
          </view>
        </picker>
      </view>

      <view class="preset-row">
        <text class="preset-chip" @click="applyPreset('1h')">1小时后</text>
        <text class="preset-chip" @click="applyPreset('tonight')">今晚20:00</text>
        <text class="preset-chip" @click="applyPreset('tomorrow')">明天9:00</text>
        <text v-if="canBeforeDue" class="preset-chip" @click="applyPreset('before_due')">截止前1小时</text>
      </view>

      <view class="date-row">
        <view class="date-cell">
          <text class="label">日期</text>
          <picker mode="date" :value="triggerDate" @change="onDateChange">
            <view class="picker-field compact">{{ triggerDate }}</view>
          </picker>
        </view>
        <view class="date-cell">
          <text class="label">时间</text>
          <picker mode="time" :value="triggerTime" @change="onTimeChange">
            <view class="picker-field compact">{{ triggerTime }}</view>
          </picker>
        </view>
      </view>

      <view class="form-group">
        <text class="label">提醒内容</text>
        <textarea
          v-model="message"
          class="message-input"
          maxlength="200"
          placeholder="可选，默认使用待办标题"
        />
      </view>

      <view class="sheet-actions">
        <TouchButton block @click="handleClose">取消</TouchButton>
        <TouchButton variant="primary" block :loading="saving" @click="handleSubmit">
          {{ mode === 'edit' ? '保存' : '创建提醒' }}
        </TouchButton>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { ReminderItem } from '../api/reminders';
import { createReminder, updateReminder } from '../api/reminders';
import { listTodos, type TodoItem } from '../api/todos';
import type { Workspace } from '../api/workspaces';
import TouchButton from './TouchButton.vue';

type ReminderPreset = '1h' | 'tonight' | 'tomorrow' | 'before_due';

const props = withDefaults(
  defineProps<{
    open: boolean;
    mode?: 'create' | 'edit';
    workspaces: Workspace[];
    initialTodo?: TodoItem | null;
    initialReminder?: ReminderItem | null;
    defaultWorkspaceId?: string | null;
  }>(),
  {
    mode: 'create',
    initialTodo: null,
    initialReminder: null,
    defaultWorkspaceId: null,
  },
);

const emit = defineEmits<{
  close: [];
  saved: [];
}>();

const saving = ref(false);
const todosLoading = ref(false);
const workspaceId = ref('');
const todoId = ref('');
const todos = ref<TodoItem[]>([]);
const triggerDate = ref(formatDate(new Date()));
const triggerTime = ref(formatTime(new Date()));
const message = ref('');

const locked = computed(() => Boolean(props.initialTodo));
const workspaceNames = computed(() => props.workspaces.map((item) => item.name));
const workspaceIndex = computed(() => Math.max(0, props.workspaces.findIndex((item) => String(item.id) === workspaceId.value)));
const selectedWorkspaceName = computed(() => props.workspaces.find((item) => String(item.id) === workspaceId.value)?.name || '');
const todoNames = computed(() => todos.value.map((item) => item.title || '未命名待办'));
const todoIndex = computed(() => Math.max(0, todos.value.findIndex((item) => item.id === todoId.value)));
const selectedTodo = computed(() => todos.value.find((item) => item.id === todoId.value) || props.initialTodo || null);
const canBeforeDue = computed(() => Boolean(buildPresetDate('before_due')));

watch(
  () => props.open,
  (open) => {
    if (open) {
      void resetForm();
    }
  },
);

async function resetForm() {
  const reminder = props.initialReminder;
  const initialTodo = props.initialTodo;
  workspaceId.value =
    String(reminder?.workspaceId || initialTodo?.workspaceId || props.defaultWorkspaceId || props.workspaces[0]?.id || '');
  todoId.value = String(reminder?.todoId || initialTodo?.id || '');
  const suggested = reminder?.triggerAt ? new Date(reminder.triggerAt) : buildPresetDate('1h') || addHours(new Date(), 1);
  triggerDate.value = formatDate(suggested);
  triggerTime.value = formatTime(suggested);
  message.value = reminder?.message || (initialTodo ? `待办提醒：${initialTodo.title}` : '');
  await loadTodos();
}

async function loadTodos() {
  if (!workspaceId.value) {
    todos.value = props.initialTodo ? [props.initialTodo] : [];
    return;
  }
  todosLoading.value = true;
  try {
    const data = await listTodos({ workspaceId: workspaceId.value, page: 1, size: 100 });
    const records = data.records || [];
    if (props.initialTodo && !records.some((item) => item.id === props.initialTodo?.id)) {
      todos.value = [props.initialTodo, ...records];
    } else {
      todos.value = records;
    }
    if (!todoId.value && todos.value.length) {
      todoId.value = todos.value[0].id;
      if (!message.value) message.value = `待办提醒：${todos.value[0].title}`;
    }
  } catch (e: any) {
    uni.showToast({ title: e?.message || '待办加载失败', icon: 'none' });
  } finally {
    todosLoading.value = false;
  }
}

async function onWorkspaceChange(e: { detail: { value: number | string } }) {
  const index = Number(e.detail.value);
  workspaceId.value = String(props.workspaces[index]?.id || '');
  todoId.value = '';
  message.value = '';
  await loadTodos();
}

function onTodoChange(e: { detail: { value: number | string } }) {
  const index = Number(e.detail.value);
  const todo = todos.value[index];
  todoId.value = todo?.id || '';
  if (todo && !message.value) {
    message.value = `待办提醒：${todo.title}`;
  }
}

function onDateChange(e: { detail: { value: string } }) {
  triggerDate.value = e.detail.value;
}

function onTimeChange(e: { detail: { value: string } }) {
  triggerTime.value = e.detail.value;
}

function applyPreset(preset: ReminderPreset) {
  const next = buildPresetDate(preset);
  if (!next) {
    uni.showToast({ title: '该快捷时间不可用', icon: 'none' });
    return;
  }
  triggerDate.value = formatDate(next);
  triggerTime.value = formatTime(next);
}

async function handleSubmit() {
  if (!workspaceId.value || !todoId.value) {
    uni.showToast({ title: '请选择知识库和待办', icon: 'none' });
    return;
  }
  const triggerAt = `${triggerDate.value}T${triggerTime.value}:00`;
  if (new Date(triggerAt).getTime() <= Date.now()) {
    uni.showToast({ title: '提醒时间需要晚于现在', icon: 'none' });
    return;
  }
  saving.value = true;
  try {
    const payload = {
      reminderType: 'once',
      triggerAt,
      message: message.value.trim() || undefined,
    };
    if (props.mode === 'edit' && props.initialReminder) {
      await updateReminder(props.initialReminder.id, payload);
      uni.showToast({ title: '提醒已更新', icon: 'success' });
    } else {
      await createReminder({
        workspaceId: workspaceId.value,
        todoId: todoId.value,
        ...payload,
      });
      uni.showToast({ title: '提醒已创建', icon: 'success' });
    }
    emit('saved');
  } catch (e: any) {
    uni.showToast({ title: e?.message || '保存失败', icon: 'none' });
  } finally {
    saving.value = false;
  }
}

function handleClose() {
  if (!saving.value) emit('close');
}

function buildPresetDate(preset: ReminderPreset) {
  const now = new Date();
  if (preset === '1h') return addHours(now, 1);
  if (preset === 'tonight') {
    const next = new Date(now);
    next.setHours(20, 0, 0, 0);
    if (next.getTime() <= now.getTime()) next.setDate(next.getDate() + 1);
    return next;
  }
  if (preset === 'tomorrow') {
    const next = new Date(now);
    next.setDate(next.getDate() + 1);
    next.setHours(9, 0, 0, 0);
    return next;
  }
  const dueAt = selectedTodo.value?.dueAt;
  if (!dueAt) return null;
  const next = addHours(new Date(dueAt), -1);
  return next.getTime() > now.getTime() ? next : null;
}

function addHours(date: Date, hours: number) {
  return new Date(date.getTime() + hours * 60 * 60 * 1000);
}

function pad(value: number) {
  return `${value}`.padStart(2, '0');
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function formatTime(date: Date) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
</script>

<style scoped lang="scss">
.sheet-mask {
  position: fixed;
  inset: 0;
  z-index: 99;
  display: flex;
  align-items: flex-end;
  background: rgba(28, 25, 23, 0.36);
}

.sheet {
  width: 100%;
  max-height: 88vh;
  overflow-y: auto;
  box-sizing: border-box;
  padding: 16rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));
  border-radius: 28rpx 28rpx 0 0;
  background: #f7f6f3;
}

.sheet-handle {
  width: 76rpx;
  height: 8rpx;
  margin: 0 auto 22rpx;
  border-radius: 99rpx;
  background: #d6d3d1;
}

.sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 26rpx;
}

.sheet-title {
  display: block;
  font-size: 38rpx;
  line-height: 1.2;
  font-weight: 700;
  color: #1c1917;
}

.sheet-subtitle {
  display: block;
  max-width: 520rpx;
  margin-top: 8rpx;
  color: #78716c;
  font-size: 24rpx;
  line-height: 1.5;
}

.sheet-close {
  flex: 0 0 auto;
  color: #0891b2;
  font-size: 26rpx;
  line-height: 48rpx;
}

.form-group {
  margin-bottom: 22rpx;
}

.label {
  display: block;
  margin-bottom: 10rpx;
  color: #57534e;
  font-size: 24rpx;
  font-weight: 600;
}

.picker-field {
  min-height: 84rpx;
  padding: 0 22rpx;
  border: 1rpx solid #e7e5e4;
  border-radius: 16rpx;
  background: #fff;
  color: #1c1917;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
}

.picker-field.disabled {
  color: #78716c;
  background: #f5f5f4;
}

.picker-field.compact {
  justify-content: center;
}

.picker-arrow {
  color: #a8a29e;
  transform: rotate(90deg);
}

.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 22rpx;
}

.preset-chip {
  padding: 14rpx 18rpx;
  border-radius: 999rpx;
  border: 1rpx solid #bae6fd;
  background: #ecfeff;
  color: #0e7490;
  font-size: 24rpx;
  font-weight: 600;
}

.date-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-bottom: 22rpx;
}

.message-input {
  width: 100%;
  min-height: 144rpx;
  padding: 20rpx 22rpx;
  border: 1rpx solid #e7e5e4;
  border-radius: 16rpx;
  background: #fff;
  color: #1c1917;
  font-size: 28rpx;
  line-height: 1.5;
  box-sizing: border-box;
}

.sheet-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-top: 28rpx;
}
</style>
