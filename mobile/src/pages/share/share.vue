<template>
  <view class="share-page">
    <view class="brand">
      <text class="brand-mark">知微</text>
      <text class="brand-sub">Noto 分享</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>

    <view v-else-if="error" class="state-card">
      <text class="state-title">{{ error }}</text>
      <text class="state-desc">链接可能已失效、过期或被创建者关闭</text>
    </view>

    <view v-else-if="passwordGate" class="state-card">
      <text class="state-title">{{ gateTitle }}</text>
      <text class="state-desc">此分享已设置访问密码</text>
      <input v-model="passwordInput" class="input" password placeholder="请输入访问密码" />
      <button class="btn-primary" :loading="unlocking" @click="handleUnlock">查看内容</button>
    </view>

    <template v-else-if="content">
      <view v-if="content.resourceType === 'NOTE'" class="content-card">
        <text class="title">{{ content.title || '未命名文档' }}</text>
        <text class="meta">{{ shareMeta }}</text>
        <text class="note-body">{{ content.content || '空文档' }}</text>
      </view>

      <view v-else-if="content.resourceType === 'BATCH'" class="content-card">
        <text class="title">{{ content.title || '批量分享' }}</text>
        <text class="meta">{{ shareMeta }}</text>
        <view v-for="file in content.files || []" :key="file.id" class="file-row" @click="openFile(file.fileUrl)">
          <view class="file-main">
            <text class="file-name">{{ file.fileName }}</text>
            <text class="meta">{{ formatFileSize(file.fileSize) }}</text>
          </view>
          <text class="open">打开</text>
        </view>
      </view>

      <view v-else class="content-card file-card">
        <view class="file-badge">FILE</view>
        <text class="title">{{ content.fileName || content.title || '共享文件' }}</text>
        <text class="meta">{{ fileMeta }}</text>
        <button class="btn-primary" @click="openFile(content.fileUrl)">查看 / 下载</button>
        <image
          v-if="isImage(content.fileType) && content.fileUrl"
          class="preview"
          :src="resolveFileUrl(content.fileUrl)"
          mode="widthFix"
        />
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { getPublicShare, unlockPublicShare, type SharedContent } from '../../api/share';
import { formatDateTime, formatFileSize } from '../../utils/format';
import { openExternalUrl, resolveFileUrl } from '../../utils/openFile';

const loading = ref(false);
const unlocking = ref(false);
const error = ref('');
const passwordGate = ref(false);
const gateTitle = ref('受保护的分享');
const passwordInput = ref('');
const content = ref<SharedContent | null>(null);
const shareToken = ref('');

const shareMeta = computed(() => {
  if (!content.value) return '';
  const parts: string[] = [];
  if (content.value.sharedAt) parts.push(`分享于 ${formatDateTime(content.value.sharedAt)}`);
  if (content.value.viewCount != null) parts.push(`访问 ${content.value.viewCount} 次`);
  return parts.join(' · ');
});

const fileMeta = computed(() => {
  if (!content.value) return '';
  const parts: string[] = [];
  if (content.value.fileSize) parts.push(formatFileSize(content.value.fileSize));
  if (content.value.sharedAt) parts.push(`分享于 ${formatDateTime(content.value.sharedAt)}`);
  if (content.value.viewCount != null) parts.push(`访问 ${content.value.viewCount} 次`);
  return parts.join(' · ');
});

function applyContent(data: SharedContent) {
  if (data.passwordRequired) {
    passwordGate.value = true;
    gateTitle.value = data.title || '受保护的分享';
    content.value = null;
    return;
  }
  passwordGate.value = false;
  content.value = data;
}

function isImage(fileType?: string) {
  return Boolean(fileType && fileType.startsWith('image/'));
}

function openFile(url?: string) {
  openExternalUrl(url, '共享文件');
}

async function loadShare(token: string) {
  loading.value = true;
  error.value = '';
  try {
    applyContent(await getPublicShare(token));
  } catch (e: any) {
    error.value = e?.message || '分享内容不可用';
  } finally {
    loading.value = false;
  }
}

async function handleUnlock() {
  if (!shareToken.value || !passwordInput.value.trim()) {
    uni.showToast({ title: '请输入访问密码', icon: 'none' });
    return;
  }
  unlocking.value = true;
  try {
    applyContent(await unlockPublicShare(shareToken.value, passwordInput.value));
    if (!passwordGate.value) uni.showToast({ title: '验证成功', icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '密码错误', icon: 'none' });
  } finally {
    unlocking.value = false;
  }
}

onLoad((query) => {
  const token = String(query?.token || '');
  if (!token) {
    error.value = '无效的分享链接';
    return;
  }
  shareToken.value = token;
  void loadShare(token);
});
</script>

<style scoped lang="scss">
.share-page {
  min-height: 100vh;
  padding: 28rpx 24rpx 60rpx;
  background: linear-gradient(180deg, #f6f3ee 0%, #eef7f6 100%);
  box-sizing: border-box;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-bottom: 28rpx;
}

.brand-mark {
  color: #17201f;
  font-size: 34rpx;
  font-weight: 760;
}

.brand-sub {
  color: #78716c;
  font-size: 24rpx;
}

.state-card,
.content-card {
  padding: 34rpx 30rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 14rpx 36rpx rgba(68, 64, 60, 0.08);
}

.state-title,
.title {
  display: block;
  color: #1c1917;
  font-size: 42rpx;
  font-weight: 760;
  line-height: 1.25;
  margin-bottom: 12rpx;
}

.state-desc,
.meta {
  display: block;
  color: #78716c;
  font-size: 24rpx;
  line-height: 1.5;
  margin-bottom: 26rpx;
}

.input {
  height: 88rpx;
  padding: 0 24rpx;
  margin-bottom: 22rpx;
  border: 1rpx solid #e7e5e4;
  border-radius: 16rpx;
  background: #fafaf9;
  box-sizing: border-box;
}

.note-body {
  display: block;
  white-space: pre-wrap;
  color: #292524;
  font-size: 30rpx;
  line-height: 1.9;
}

.file-card {
  text-align: center;
}

.file-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 68rpx;
  padding: 0 22rpx;
  margin-bottom: 18rpx;
  border-radius: 999rpx;
  background: #ecfeff;
  color: #0891b2;
  font-weight: 760;
  letter-spacing: 0;
}

.file-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f5f5f4;
}

.file-row:last-child {
  border-bottom: 0;
}

.file-main {
  flex: 1;
  min-width: 0;
}

.file-name {
  display: block;
  color: #292524;
  font-size: 29rpx;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.open {
  color: #0f766e;
  font-size: 26rpx;
  font-weight: 700;
}

.preview {
  width: 100%;
  margin-top: 28rpx;
  border-radius: 18rpx;
}
</style>
