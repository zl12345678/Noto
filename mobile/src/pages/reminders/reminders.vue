<template>
  <view class="page-safe">
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
    <view v-else-if="!items.length" class="empty card">暂无提醒</view>

    <view v-for="item in items" :key="item.id" class="card item">
      <view class="head">
        <text class="tag">{{ REMINDER_STATUS_LABEL[item.status] }}</text>
        <text class="muted">{{ formatDateTime(item.triggerAt) }}</text>
      </view>
      <text class="title">{{ item.todoTitle || '待办' }}</text>
      <text v-if="item.message" class="msg muted">{{ item.message }}</text>
      <text v-if="item.noteId" class="link" @click="openNote(item.noteId!)">查看关联笔记 ›</text>
      <view v-if="item.status === REMINDER_STATUS.PENDING" class="action-row">
        <TouchButton block @click="onCancel(item.id)">取消提醒</TouchButton>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import TouchButton from '../../components/TouchButton.vue';
import {
  cancelReminder,
  listReminders,
  REMINDER_STATUS,
  REMINDER_STATUS_LABEL,
  type ReminderItem,
} from '../../api/reminders';
import { ensureAuthPage } from '../../stores/auth';
import { formatDateTime } from '../../utils/format';

const items = ref<ReminderItem[]>([]);
const loading = ref(false);
const statusIndex = ref(0);
const statusLabels = ['全部', '待触发', '已触发'];
const statusValues: Array<number | null> = [null, 0, 1];

function openNote(id: string) {
  uni.navigateTo({ url: `/pages/notes/detail?id=${id}` });
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
  await cancelReminder(id);
  await load();
}

onShow(() => {
  if (!ensureAuthPage()) return;
  load();
});

onPullDownRefresh(async () => {
  await load();
  uni.stopPullDownRefresh();
});
</script>

<style scoped lang="scss">
.item .head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.tag { color: #0891b2; font-size: 24rpx; font-weight: 600; }
.title { display: block; font-size: 32rpx; font-weight: 600; margin-bottom: 8rpx; }
.link { display: block; margin-top: 16rpx; color: #0891b2; font-size: 28rpx; }
.action-row { margin-top: 20rpx; }
</style>
