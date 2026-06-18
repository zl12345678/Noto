<template>
  <view class="page">
    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!items.length" class="empty card">暂无分享</view>
    <view v-for="item in items" :key="item.token" class="card share">
      <text class="title">{{ item.title || '未命名' }}</text>
      <text class="muted">{{ typeLabel(item.resourceType) }} · 浏览 {{ item.viewCount ?? 0 }} 次</text>
      <text v-if="item.passwordProtected" class="muted">已设密码</text>
      <text class="muted">创建于 {{ formatDateTime(item.createdAt) }}</text>
      <view class="actions">
        <button size="mini" @click="copyLink(item)">复制链接</button>
        <button size="mini" @click="onRevoke(item.token)">撤销</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listMyShares, revokeShareByToken, type ShareLink } from '../../api/share';
import { ensureAuthPage } from '../../stores/auth';
import { formatDateTime } from '../../utils/format';

const items = ref<ShareLink[]>([]);
const loading = ref(false);

function typeLabel(type: ShareLink['resourceType']) {
  if (type === 'NOTE') return '文档';
  if (type === 'ATTACHMENT') return '附件';
  return '批量';
}

function copyLink(item: ShareLink) {
  const path = item.sharePath.startsWith('/') ? item.sharePath : `/${item.sharePath}`;
  // #ifdef H5
  const url = `${window.location.origin}${path}`;
  navigator.clipboard?.writeText(url);
  // #endif
  uni.setClipboardData({ data: path, success: () => uni.showToast({ title: '已复制路径', icon: 'none' }) });
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
    success: async (res) => {
      if (res.confirm) {
        await revokeShareByToken(token);
        await load();
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
.share .title { display: block; font-size: 30rpx; font-weight: 600; margin-bottom: 8rpx; }
.actions { display: flex; gap: 16rpx; margin-top: 16rpx; }
</style>
