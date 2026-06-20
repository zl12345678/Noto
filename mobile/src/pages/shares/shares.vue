<template>
  <view class="page share-page">
    <view class="share-head card">
      <view>
        <text class="eyebrow">Share</text>
        <text class="page-title">我的分享</text>
        <text class="head-desc">集中查看、复制、预览或关闭已创建的分享</text>
      </view>
      <button size="mini" class="refresh-btn" :loading="loading" @click="load">刷新</button>
    </view>

    <view class="type-filter">
      <text
        v-for="option in typeFilterOptions"
        :key="option.value"
        class="type-item"
        :class="{ active: typeFilter === option.value }"
        @click="typeFilter = option.value"
      >{{ option.label }}</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!filteredItems.length" class="empty card">{{ emptyText }}</view>
    <view v-for="item in filteredItems" :key="item.token" class="card share">
      <view class="share-meta-row">
        <text class="type-chip" :class="item.resourceType.toLowerCase()">{{ typeLabel(item.resourceType) }}</text>
        <text v-if="item.passwordProtected" class="share-chip">需密码</text>
        <text class="share-chip">访问 {{ item.viewCount ?? 0 }}</text>
      </view>
      <text class="title">{{ item.title || '未命名' }}</text>
      <text class="muted">{{ item.expiresAt ? `有效期至 ${formatDateTime(item.expiresAt)}` : '永久有效' }}</text>
      <text v-if="item.createdAt" class="muted">创建于 {{ formatDateTime(item.createdAt) }}</text>
      <view class="actions">
        <button size="mini" @click="copyLink(item)">复制链接</button>
        <button size="mini" @click="openShareActions(item)">更多</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { buildShareUrl, listMyShares, revokeShareByToken, tokenFromSharePath, type ShareLink } from '../../api/share';
import { ensureAuthPage } from '../../stores/auth';
import { formatDateTime } from '../../utils/format';

const items = ref<ShareLink[]>([]);
const loading = ref(false);
const typeFilter = ref<'all' | ShareLink['resourceType']>('all');

const typeFilterOptions = [
  { label: '全部', value: 'all' as const },
  { label: '文档', value: 'NOTE' as const },
  { label: '文件', value: 'ATTACHMENT' as const },
  { label: '批量', value: 'BATCH' as const },
];

const filteredItems = computed(() => {
  if (typeFilter.value === 'all') return items.value;
  return items.value.filter((item) => item.resourceType === typeFilter.value);
});

const emptyText = computed(() => {
  if (typeFilter.value === 'all') return '暂无分享';
  return `暂无${typeLabel(typeFilter.value)}分享`;
});

function typeLabel(type: ShareLink['resourceType']) {
  if (type === 'NOTE') return '文档';
  if (type === 'ATTACHMENT') return '附件';
  return '批量';
}

function copyLink(item: ShareLink) {
  const url = buildShareUrl(item.sharePath);
  uni.setClipboardData({ data: url, success: () => uni.showToast({ title: '已复制链接', icon: 'none' }) });
}

function openShare(item: ShareLink) {
  uni.navigateTo({ url: `/pages/share/share?token=${encodeURIComponent(tokenFromSharePath(item.sharePath))}` });
}

function canOpenSource(item: ShareLink) {
  if (item.resourceType === 'BATCH') return false;
  if (!item.resourceId) return false;
  return !String(item.title || '').includes('已删除');
}

function openSource(item: ShareLink) {
  if (!item.resourceId) return;
  if (item.resourceType === 'NOTE') {
    uni.navigateTo({ url: `/pages/notes/detail?id=${item.resourceId}` });
    return;
  }
  if (item.resourceType === 'ATTACHMENT') {
    uni.navigateTo({ url: '/pages/drive/drive' });
  }
}

function openShareActions(item: ShareLink) {
  const actions = ['预览'];
  if (canOpenSource(item)) actions.push('打开来源');
  actions.push('撤销分享');
  uni.showActionSheet({
    itemList: actions,
    success: (res) => {
      const label = actions[res.tapIndex];
      if (label === '预览') openShare(item);
      if (label === '打开来源') openSource(item);
      if (label === '撤销分享') void onRevoke(item.token);
    },
  });
}

async function load() {
  loading.value = true;
  try {
    items.value = await listMyShares();
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

async function onRevoke(token: string) {
  uni.showModal({
    title: '撤销分享',
    content: '撤销后链接将失效',
    confirmColor: '#b91c1c',
    success: async (res) => {
      if (res.confirm) {
        try {
          await revokeShareByToken(token);
          items.value = items.value.filter((item) => item.token !== token);
          uni.showToast({ title: '已撤销分享', icon: 'none' });
        } catch (e: any) {
          uni.showToast({ title: e?.message || '撤销失败', icon: 'none' });
        }
      }
    },
  });
}

onShow(() => {
  if (!ensureAuthPage()) return;
  load();
});
</script>

<style scoped lang="scss">
.page { padding: 24rpx; }

.share-head {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  padding: 28rpx;
  margin-bottom: 18rpx;
  background: #17201f;
  color: #fff;
}

.eyebrow {
  display: block;
  color: rgba(255, 255, 255, 0.58);
  font-size: 22rpx;
  font-weight: 650;
  margin-bottom: 8rpx;
}

.page-title {
  display: block;
  font-size: 44rpx;
  font-weight: 760;
}

.head-desc {
  display: block;
  margin-top: 10rpx;
  color: rgba(255, 255, 255, 0.68);
  font-size: 24rpx;
  line-height: 1.45;
}

.refresh-btn {
  flex: 0 0 auto;
  align-self: flex-start;
  margin: 0;
  color: #0f766e;
  background: #ecfeff;
  border-radius: 999rpx;
}

.type-filter {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10rpx;
  margin-bottom: 18rpx;
}

.type-item {
  height: 62rpx;
  line-height: 62rpx;
  border-radius: 14rpx;
  background: #fff;
  color: #78716c;
  text-align: center;
  font-size: 24rpx;
  font-weight: 650;
}

.type-item.active {
  background: #ecfeff;
  color: #0e7490;
}

.share {
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.share-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-bottom: 12rpx;
}

.type-chip,
.share-chip {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  font-size: 21rpx;
  font-weight: 650;
}

.type-chip {
  background: #ecfeff;
  color: #0e7490;
}

.type-chip.note {
  background: #eff6ff;
  color: #1d4ed8;
}

.type-chip.batch {
  background: #f5f3ff;
  color: #6d28d9;
}

.share-chip {
  background: #f5f5f4;
  color: #78716c;
}

.share .title {
  display: block;
  color: #1c1917;
  font-size: 31rpx;
  font-weight: 680;
  line-height: 1.45;
  margin-bottom: 10rpx;
}

.actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130rpx, 1fr));
  gap: 12rpx;
  margin-top: 16rpx;
}
</style>
