<template>
  <view class="page-safe">
    <view v-if="stats && stats.overdueTodos > 0" class="banner-warn">
      有 {{ stats.overdueTodos }} 项待办已逾期，优先处理
    </view>

    <view class="stats card">
      <view class="stat-item">
        <text class="stat-num">{{ stats?.pendingTodos ?? '-' }}</text>
        <text class="stat-label">待办</text>
      </view>
      <view class="stat-item">
        <text class="stat-num success">{{ stats?.todayCompletedTodos ?? '-' }}</text>
        <text class="stat-label">今日完成</text>
      </view>
      <view class="stat-item" @click="goReminders">
        <text class="stat-num">{{ stats?.overdueTodos ?? '-' }}</text>
        <text class="stat-label">逾期</text>
      </view>
    </view>

    <view class="section">
      <view class="section-head">
        <text class="section-title">待办队列</text>
        <text class="link" @click="goTodos">全部 ›</text>
      </view>
      <view v-if="loading" class="empty">加载中…</view>
      <view v-else-if="!previewQueue.length" class="empty card">队列为空，去待办页新建</view>
      <view v-for="item in previewQueue" :key="item.id" class="card todo-card">
        <text class="todo-title">{{ item.title }}</text>
        <text v-if="item.noteTitle" class="muted">来自 {{ item.noteTitle }}</text>
        <TouchButton variant="primary" block @click="startTodo(item.id)">开始执行</TouchButton>
      </view>
    </view>

    <view class="section">
      <view class="section-head">
        <text class="section-title">进行中</text>
        <text class="muted">{{ stats?.parallelTodos?.length || 0 }} 项</text>
      </view>
      <view v-if="!previewActive.length" class="muted empty-inline">暂无进行中任务</view>
      <view v-for="item in previewActive" :key="item.id" class="card todo-card">
        <text class="todo-title">{{ item.title }}</text>
        <view class="action-row">
          <TouchButton variant="primary" block @click="completeTodo(item.id)">完成</TouchButton>
          <TouchButton block @click="backTodo(item.id)">退回</TouchButton>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import TouchButton from '../../components/TouchButton.vue';
import { getDashboardStats, type DashboardStats } from '../../api/dashboard';
import { patchTodoStatus, TODO_STATUS } from '../../api/todos';
import { ensureAuthPage } from '../../stores/auth';

const stats = ref<DashboardStats | null>(null);
const loading = ref(false);

const previewQueue = computed(() => stats.value?.actionTodos?.slice(0, 3) || []);
const previewActive = computed(() => stats.value?.parallelTodos?.slice(0, 3) || []);

async function load() {
  loading.value = true;
  try {
    stats.value = await getDashboardStats();
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

async function startTodo(id: string) {
  await patchTodoStatus(id, TODO_STATUS.IN_PROGRESS);
  uni.showToast({ title: '已开始', icon: 'success' });
  await load();
}

async function completeTodo(id: string) {
  await patchTodoStatus(id, TODO_STATUS.COMPLETED);
  uni.showToast({ title: '已完成', icon: 'success' });
  await load();
}

async function backTodo(id: string) {
  await patchTodoStatus(id, TODO_STATUS.PENDING);
  await load();
}

function goTodos() {
  uni.switchTab({ url: '/pages/todos/todos' });
}

function goReminders() {
  uni.navigateTo({ url: '/pages/reminders/reminders' });
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
.stats {
  display: flex;
  justify-content: space-around;
  padding: 32rpx 16rpx;
}

.stat-item { text-align: center; }

.stat-num {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: #0891b2;
}

.stat-num.success { color: #059669; }

.stat-label {
  font-size: 24rpx;
  color: #78716c;
}

.section { margin-top: 8rpx; }

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.link { color: #0891b2; font-size: 26rpx; }

.todo-card .todo-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}

.todo-card .touch-btn {
  margin-top: 20rpx;
}

.action-row {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 20rpx;
}

.empty-inline {
  padding: 24rpx 0;
  text-align: center;
}
</style>
