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
        <text class="section-title">今日</text>
        <text class="link" @click="refreshDigest">{{ digest ? '刷新建议' : '生成建议' }}</text>
      </view>
      <view class="card today-card">
        <view v-if="digestLoading" class="muted empty-inline">AI 正在整理今日行动…</view>
        <template v-else-if="digest?.suggestions">
          <text class="ai-summary">{{ digest.suggestions.summary || '今日建议已生成' }}</text>
          <view v-for="item in digest.suggestions.suggestions?.slice(0, 3)" :key="item.todoId" class="ai-item">
            <text class="ai-title">{{ item.title }}</text>
            <text class="muted">{{ item.reason }}</text>
          </view>
        </template>
        <view v-else class="ai-empty">
          <text class="muted">还没有今日建议</text>
          <TouchButton variant="primary" block @click="refreshDigest">生成今日建议</TouchButton>
        </view>

        <view class="today-actions">
          <view class="today-action" @click="openDailyReview">
            <text class="today-action-title">今日复盘</text>
            <text class="today-action-desc">{{ dailyReview?.completedCount ? `已完成 ${dailyReview.completedCount} 项` : '看进展和明日重点' }}</text>
          </view>
          <view class="today-action" @click="weeklyRetro?.noteId ? openWeeklyRetro() : refreshWeeklyRetro()">
            <text class="today-action-title">每周复盘</text>
            <text class="today-action-desc">{{ weeklyRetro?.noteTitle || (weeklyRetroLoading ? '生成中…' : '生成本周复盘') }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="section">
      <view class="section-head">
        <text class="section-title">行动清单</text>
        <text class="link" @click="goTodos">全部 ›</text>
      </view>
      <view v-if="loading" class="empty">加载中…</view>
      <view v-else-if="!previewQueue.length && !previewActive.length" class="empty card">清单为空，去待办页新建</view>
      <view v-for="item in previewActive" :key="`active-${item.id}`" class="card todo-card">
        <view class="todo-head">
          <text class="todo-title">{{ item.title }}</text>
          <text class="status-tag active">进行中</text>
        </view>
        <view class="action-row compact">
          <TouchButton variant="primary" block @click="completeTodo(item.id)">完成</TouchButton>
          <TouchButton block @click="backTodo(item.id)">退回</TouchButton>
        </view>
      </view>
      <view v-for="item in previewQueue" :key="`queue-${item.id}`" class="card todo-card">
        <view class="todo-head">
          <view>
            <text class="todo-title">{{ item.title }}</text>
            <text v-if="item.noteTitle" class="muted">来自 {{ item.noteTitle }}</text>
          </view>
          <text class="status-tag">待开始</text>
        </view>
        <TouchButton variant="primary" block @click="startTodo(item.id)">开始执行</TouchButton>
      </view>
    </view>

    <view class="section">
      <view class="section-head">
        <text class="section-title subtle">最近文档</text>
        <text class="link" @click="goNotes">{{ stats?.totalNotes ?? 0 }} 篇 ›</text>
      </view>
      <view v-if="!recentNotes.length" class="empty card">还没有文档，去笔记页新建</view>
      <view v-for="note in recentNotes" :key="note.id" class="card note-card" @click="openNote(note.id)">
        <text class="note-title">{{ note.title || '无标题' }}</text>
        <text class="muted">{{ note.summary || note.excerpt || '暂无摘要' }}</text>
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
import {
  generateAiDigest,
  generateWeeklyRetro,
  getAiDailyReview,
  getCurrentWeeklyRetro,
  getTodayAiDigest,
  type AiDailyReview,
  type AiDigest,
  type AiWeeklyRetro,
} from '../../api/ai';
import { ensureAuthPage } from '../../stores/auth';

const stats = ref<DashboardStats | null>(null);
const digest = ref<AiDigest | null>(null);
const weeklyRetro = ref<AiWeeklyRetro | null>(null);
const dailyReview = ref<AiDailyReview | null>(null);
const loading = ref(false);
const digestLoading = ref(false);
const weeklyRetroLoading = ref(false);
const dailyReviewLoading = ref(false);

const previewQueue = computed(() => stats.value?.actionTodos?.slice(0, 3) || []);
const previewActive = computed(() => stats.value?.parallelTodos?.slice(0, 3) || []);
const recentNotes = computed(() => stats.value?.recentNotes?.slice(0, 4) || []);

async function load() {
  loading.value = true;
  try {
    stats.value = await getDashboardStats();
    void loadAiCards();
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

async function loadAiCards() {
  try {
    const [today, retro, review] = await Promise.all([getTodayAiDigest(), getCurrentWeeklyRetro(), getAiDailyReview()]);
    digest.value = today;
    weeklyRetro.value = retro;
    dailyReview.value = review;
  } catch {
    digest.value = null;
    weeklyRetro.value = null;
    dailyReview.value = null;
  }
}

async function refreshDigest() {
  digestLoading.value = true;
  try {
    digest.value = await generateAiDigest();
    uni.showToast({ title: '今日建议已生成', icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '生成失败', icon: 'none' });
  } finally {
    digestLoading.value = false;
  }
}

async function refreshWeeklyRetro() {
  weeklyRetroLoading.value = true;
  try {
    weeklyRetro.value = await generateWeeklyRetro();
    uni.showToast({ title: '复盘已生成', icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '生成失败', icon: 'none' });
  } finally {
    weeklyRetroLoading.value = false;
  }
}

function openWeeklyRetro() {
  if (!weeklyRetro.value?.noteId) return;
  uni.navigateTo({ url: `/pages/notes/detail?id=${weeklyRetro.value.noteId}` });
}

async function openDailyReview() {
  dailyReviewLoading.value = true;
  try {
    dailyReview.value = await getAiDailyReview();
    const review = dailyReview.value;
    if (!review?.summary) {
      uni.showToast({ title: '暂无复盘内容', icon: 'none' });
      return;
    }
    const details = [
      review.summary,
      review.highlights?.length ? `\n亮点：\n${review.highlights.map((item) => `- ${item}`).join('\n')}` : '',
      review.blockers?.length ? `\n卡点：\n${review.blockers.map((item) => `- ${item}`).join('\n')}` : '',
      review.tomorrowFocus?.length ? `\n明日建议：\n${review.tomorrowFocus.map((item) => `- ${item}`).join('\n')}` : '',
    ].filter(Boolean).join('\n');
    uni.showModal({ title: 'AI 今日复盘', content: details, showCancel: false });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '复盘生成失败', icon: 'none' });
  } finally {
    dailyReviewLoading.value = false;
  }
}

function openNote(id: string) {
  uni.navigateTo({ url: `/pages/notes/detail?id=${id}` });
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

function goNotes() {
  uni.switchTab({ url: '/pages/notes/notes' });
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

.section-title.subtle {
  color: #57534e;
  font-size: 28rpx;
}

.todo-card .todo-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}

.todo-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
}

.todo-head > view {
  flex: 1;
  min-width: 0;
}

.status-tag {
  flex: 0 0 auto;
  padding: 7rpx 12rpx;
  border-radius: 10rpx;
  background: #f5f5f4;
  color: #78716c;
  font-size: 22rpx;
  font-weight: 650;
}

.status-tag.active {
  background: #ecfeff;
  color: #0e7490;
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

.action-row.compact {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.empty-inline {
  padding: 24rpx 0;
  text-align: center;
}

.today-card {
  padding: 28rpx;
}

.ai-summary {
  display: block;
  color: #164e63;
  font-size: 29rpx;
  font-weight: 700;
  line-height: 1.55;
  margin-bottom: 18rpx;
}

.ai-item {
  padding: 18rpx 0;
  border-top: 1rpx solid #eef2f7;
}

.ai-title {
  display: block;
  color: #292524;
  font-size: 28rpx;
  font-weight: 650;
  margin-bottom: 6rpx;
}

.ai-empty {
  display: grid;
  gap: 20rpx;
}

.today-actions {
  display: grid;
  gap: 12rpx;
  margin-top: 22rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid #f1f5f9;
}

.today-action {
  padding: 16rpx 0;
}

.today-action-title {
  display: block;
  color: #1c1917;
  font-size: 28rpx;
  font-weight: 650;
}

.today-action-desc {
  display: block;
  margin-top: 6rpx;
  color: #78716c;
  font-size: 24rpx;
  line-height: 1.45;
}

.note-card {
  padding: 24rpx 26rpx;
  margin-bottom: 14rpx;
}

.note-title {
  display: block;
  color: #1c1917;
  font-size: 30rpx;
  font-weight: 680;
  line-height: 1.45;
  margin-bottom: 8rpx;
}
</style>
