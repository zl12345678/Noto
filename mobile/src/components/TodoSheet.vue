<template>
  <view v-if="open" class="sheet-mask" @click="handleClose">
    <view class="sheet" @click.stop>
      <view class="sheet-handle" />
      <view class="sheet-head">
        <view>
          <text class="sheet-title">{{ mode === 'edit' ? '编辑待办' : '新建待办' }}</text>
          <text class="sheet-subtitle">补全标题、优先级和截止时间，提醒与行动页会同步使用</text>
        </view>
        <text class="sheet-close" @click="handleClose">关闭</text>
      </view>

      <view class="form-group">
        <text class="label">知识库</text>
        <picker :disabled="mode === 'edit'" :range="workspaceNames" :value="workspaceIndex" @change="onWorkspaceChange">
          <view class="picker-field" :class="{ disabled: mode === 'edit' }">
            <text>{{ selectedWorkspaceName || '请选择知识库' }}</text>
            <text class="picker-arrow">›</text>
          </view>
        </picker>
      </view>

      <view class="form-group">
        <text class="label">标题</text>
        <input v-model="title" class="text-input" maxlength="120" placeholder="待办标题" />
      </view>

      <view class="form-group">
        <text class="label">描述</text>
        <textarea v-model="description" class="textarea-input" maxlength="500" placeholder="可选，补充背景或验收标准" />
      </view>

      <view class="form-group">
        <text class="label">类型</text>
        <view class="choice-row">
          <text
            v-for="option in horizonOptions"
            :key="option.value"
            class="choice-chip"
            :class="{ active: horizon === option.value }"
            @click="horizon = option.value"
          >{{ option.label }}</text>
        </view>
      </view>

      <view class="form-group">
        <text class="label">优先级</text>
        <view class="choice-row">
          <text
            v-for="option in priorityOptions"
            :key="option.value"
            class="choice-chip"
            :class="{ active: priority === option.value, high: option.value === 3 }"
            @click="priority = option.value"
          >{{ option.label }}</text>
        </view>
      </view>

      <view class="form-group due-line">
        <view>
          <text class="label">截止时间</text>
          <text class="hint">{{ dueEnabled ? `${dueDate} ${dueTime}` : '不设置截止时间' }}</text>
        </view>
        <switch :checked="dueEnabled" color="#0891b2" @change="onDueToggle" />
      </view>

      <view v-if="dueEnabled" class="date-row">
        <view class="date-cell">
          <text class="label">日期</text>
          <picker mode="date" :value="dueDate" @change="onDateChange">
            <view class="picker-field compact">{{ dueDate }}</view>
          </picker>
        </view>
        <view class="date-cell">
          <text class="label">时间</text>
          <picker mode="time" :value="dueTime" @change="onTimeChange">
            <view class="picker-field compact">{{ dueTime }}</view>
          </picker>
        </view>
      </view>

      <view class="sheet-actions">
        <TouchButton block @click="handleClose">取消</TouchButton>
        <TouchButton variant="primary" block :loading="saving" @click="handleSubmit">
          {{ mode === 'edit' ? '保存' : '创建' }}
        </TouchButton>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  createTodo,
  TODO_HORIZON,
  TODO_HORIZON_LABEL,
  TODO_PRIORITY,
  TODO_PRIORITY_LABEL,
  updateTodo,
  type TodoHorizonType,
  type TodoItem,
} from '../api/todos';
import type { Workspace } from '../api/workspaces';
import TouchButton from './TouchButton.vue';

const props = withDefaults(
  defineProps<{
    open: boolean;
    mode?: 'create' | 'edit';
    workspaces: Workspace[];
    initialTodo?: TodoItem | null;
    defaultWorkspaceId?: string | null;
  }>(),
  {
    mode: 'create',
    initialTodo: null,
    defaultWorkspaceId: null,
  },
);

const emit = defineEmits<{
  close: [];
  saved: [];
}>();

const saving = ref(false);
const workspaceId = ref('');
const title = ref('');
const description = ref('');
const horizon = ref<TodoHorizonType>(TODO_HORIZON.ACTION);
const priority = ref<number>(TODO_PRIORITY.MEDIUM);
const dueEnabled = ref(false);
const dueDate = ref(formatDate(new Date()));
const dueTime = ref('18:00');

const horizonOptions = [
  { value: TODO_HORIZON.ACTION, label: TODO_HORIZON_LABEL.action },
  { value: TODO_HORIZON.LONG_TERM, label: TODO_HORIZON_LABEL.long_term },
];

const priorityOptions = [
  { value: TODO_PRIORITY.LOW, label: TODO_PRIORITY_LABEL[TODO_PRIORITY.LOW] },
  { value: TODO_PRIORITY.MEDIUM, label: TODO_PRIORITY_LABEL[TODO_PRIORITY.MEDIUM] },
  { value: TODO_PRIORITY.HIGH, label: TODO_PRIORITY_LABEL[TODO_PRIORITY.HIGH] },
];

const workspaceNames = computed(() => props.workspaces.map((item) => item.name));
const workspaceIndex = computed(() => Math.max(0, props.workspaces.findIndex((item) => String(item.id) === workspaceId.value)));
const selectedWorkspaceName = computed(() => props.workspaces.find((item) => String(item.id) === workspaceId.value)?.name || '');

watch(
  () => props.open,
  (open) => {
    if (open) resetForm();
  },
);

function resetForm() {
  const todo = props.initialTodo;
  workspaceId.value = String(todo?.workspaceId || props.defaultWorkspaceId || props.workspaces[0]?.id || '');
  title.value = todo?.title || '';
  description.value = todo?.description || '';
  horizon.value = todo?.horizon || TODO_HORIZON.ACTION;
  priority.value = todo?.priority || TODO_PRIORITY.MEDIUM;
  if (todo?.dueAt) {
    const date = new Date(todo.dueAt);
    dueEnabled.value = true;
    dueDate.value = formatDate(date);
    dueTime.value = formatTime(date);
  } else {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    dueEnabled.value = false;
    dueDate.value = formatDate(tomorrow);
    dueTime.value = '18:00';
  }
}

function onWorkspaceChange(e: { detail: { value: number | string } }) {
  const index = Number(e.detail.value);
  workspaceId.value = String(props.workspaces[index]?.id || '');
}

function onDueToggle(e: any) {
  dueEnabled.value = Boolean(e?.detail?.value);
}

function onDateChange(e: { detail: { value: string } }) {
  dueDate.value = e.detail.value;
}

function onTimeChange(e: { detail: { value: string } }) {
  dueTime.value = e.detail.value;
}

async function handleSubmit() {
  const cleanTitle = title.value.trim();
  if (!workspaceId.value) {
    uni.showToast({ title: '请选择知识库', icon: 'none' });
    return;
  }
  if (!cleanTitle) {
    uni.showToast({ title: '请输入待办标题', icon: 'none' });
    return;
  }
  const dueAt = dueEnabled.value ? `${dueDate.value}T${dueTime.value}:00` : null;
  saving.value = true;
  try {
    const payload = {
      title: cleanTitle,
      description: description.value.trim() || undefined,
      priority: priority.value,
      horizon: horizon.value,
      dueAt,
      noteId: props.initialTodo?.noteId || null,
    };
    if (props.mode === 'edit' && props.initialTodo) {
      await updateTodo(props.initialTodo.id, payload);
      uni.showToast({ title: '待办已更新', icon: 'success' });
    } else {
      await createTodo({
        workspaceId: workspaceId.value,
        ...payload,
      });
      uni.showToast({ title: '待办已创建', icon: 'success' });
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
  z-index: 98;
  display: flex;
  align-items: flex-end;
  background: rgba(28, 25, 23, 0.36);
}

.sheet {
  width: 100%;
  max-height: 90vh;
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
  max-width: 540rpx;
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

.hint {
  display: block;
  color: #78716c;
  font-size: 24rpx;
}

.picker-field,
.text-input {
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

.textarea-input {
  width: 100%;
  min-height: 148rpx;
  padding: 20rpx 22rpx;
  border: 1rpx solid #e7e5e4;
  border-radius: 16rpx;
  background: #fff;
  color: #1c1917;
  font-size: 28rpx;
  line-height: 1.5;
  box-sizing: border-box;
}

.choice-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.choice-chip {
  padding: 16rpx 22rpx;
  border-radius: 999rpx;
  border: 1rpx solid #e7e5e4;
  background: #fff;
  color: #57534e;
  font-size: 26rpx;
  font-weight: 600;
}

.choice-chip.active {
  border-color: #0891b2;
  background: #ecfeff;
  color: #0e7490;
}

.choice-chip.active.high {
  border-color: #f97316;
  background: #fff7ed;
  color: #c2410c;
}

.due-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 18rpx 20rpx;
  border: 1rpx solid #e7e5e4;
  border-radius: 16rpx;
  background: #fff;
}

.date-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-bottom: 22rpx;
}

.sheet-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-top: 28rpx;
}
</style>
