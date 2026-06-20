<template>
  <view class="page-safe">
    <view class="page-head">
      <view>
        <text class="page-title">提醒</text>
        <text class="page-desc">按待办设置触发时间</text>
      </view>
      <TouchButton variant="primary" @click="openCreate">新建</TouchButton>
    </view>

    <view class="segment">
      <text
        v-for="(label, idx) in statusLabels"
        :key="label"
        class="segment-item"
        :class="{ active: statusIndex === idx }"
        @click="statusIndex = idx; load()"
      >{{ label }}</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!items.length" class="empty card">暂无提醒，点击右上角新建</view>

    <view v-for="item in items" :key="item.id" class="card item">
      <view class="head">
        <text class="tag">{{ REMINDER_STATUS_LABEL[item.status] }}</text>
        <text class="muted">{{ formatDateTime(item.triggerAt) }}</text>
      </view>
      <text class="title">{{ item.todoTitle || '待办' }}</text>
      <text v-if="item.message" class="msg muted">{{ item.message }}</text>
      <text v-if="item.noteId" class="link" @click="openNote(item.noteId!)">查看关联笔记 ›</text>
      <view class="action-row">
        <TouchButton
          v-if="item.status === REMINDER_STATUS.PENDING"
          block
          @click="openEdit(item)"
        >编辑</TouchButton>
        <TouchButton block @click="openReminderActions(item)">更多</TouchButton>
      </view>
    </view>

    <ReminderSheet
      :open="sheetOpen"
      :mode="sheetMode"
      :workspaces="workspaceState.items"
      :initial-reminder="editingReminder"
      :default-workspace-id="defaultWorkspaceId"
      @close="closeSheet"
      @saved="onSheetSaved"
    />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import ReminderSheet from '../../components/ReminderSheet.vue';
import TouchButton from '../../components/TouchButton.vue';
import {
  cancelReminder,
  deleteReminder,
  listReminders,
  REMINDER_STATUS,
  REMINDER_STATUS_LABEL,
  type ReminderItem,
} from '../../api/reminders';
import { ensureAuthPage } from '../../stores/auth';
import { refreshWorkspaces, workspaceState } from '../../stores/workspace';
import { formatDateTime } from '../../utils/format';

const items = ref<ReminderItem[]>([]);
const loading = ref(false);
const statusIndex = ref(0);
const sheetOpen = ref(false);
const sheetMode = ref<'create' | 'edit'>('create');
const editingReminder = ref<ReminderItem | null>(null);
const statusLabels = ['全部', '待触发', '已触发', '已取消'];
const statusValues: Array<number | null> = [null, 0, 1, 2];
const defaultWorkspaceId = computed(() => editingReminder.value?.workspaceId || workspaceState.items[0]?.id || null);

function openNote(id: string) {
  uni.navigateTo({ url: `/pages/notes/detail?id=${id}` });
}

function openCreate() {
  if (!workspaceState.items.length) {
    uni.showToast({ title: '暂无知识库', icon: 'none' });
    return;
  }
  sheetMode.value = 'create';
  editingReminder.value = null;
  sheetOpen.value = true;
}

function openEdit(item: ReminderItem) {
  sheetMode.value = 'edit';
  editingReminder.value = item;
  sheetOpen.value = true;
}

function closeSheet() {
  sheetOpen.value = false;
  editingReminder.value = null;
}

async function onSheetSaved() {
  closeSheet();
  await load();
}

async function load() {
  loading.value = true;
  try {
    const data = await listReminders({ page: 1, size: 50, status: statusValues[statusIndex.value] });
    items.value = data.records;
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

async function onCancel(id: string) {
  uni.showModal({
    title: '取消提醒',
    content: '确定取消这条提醒吗？',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await cancelReminder(id);
        uni.showToast({ title: '已取消', icon: 'none' });
        await load();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '取消失败', icon: 'none' });
      }
    },
  });
}

function openReminderActions(item: ReminderItem) {
  const actions = item.status === REMINDER_STATUS.PENDING ? ['取消提醒', '删除'] : ['删除'];
  uni.showActionSheet({
    itemList: actions,
    success: (res) => {
      const label = actions[res.tapIndex];
      if (label === '取消提醒') void onCancel(item.id);
      if (label === '删除') void onDelete(item.id);
    },
  });
}

async function onDelete(id: string) {
  uni.showModal({
    title: '删除提醒',
    content: '删除后不可恢复，确定删除吗？',
    confirmColor: '#b91c1c',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await deleteReminder(id);
        uni.showToast({ title: '已删除', icon: 'none' });
        await load();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '删除失败', icon: 'none' });
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
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.page-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #1c1917;
}

.page-desc {
  display: block;
  margin-top: 6rpx;
  color: #78716c;
  font-size: 24rpx;
}

.item .head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.tag { color: #0891b2; font-size: 24rpx; font-weight: 600; }
.title { display: block; font-size: 32rpx; font-weight: 600; margin-bottom: 8rpx; }
.link { display: block; margin-top: 16rpx; color: #0891b2; font-size: 28rpx; }
.action-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12rpx;
  margin-top: 20rpx;
}
</style>
