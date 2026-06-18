<template>
  <view class="page">
    <view class="toolbar card">
      <picker :range="workspaceNames" :value="workspaceIndex" @change="onWorkspaceChange">
        <view class="picker">知识库：{{ currentWorkspaceName || '请选择' }}</view>
      </picker>
      <input v-model="keyword" class="search" placeholder="搜索文件名" confirm-type="search" @confirm="load" />
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!files.length" class="empty card">暂无文件</view>
    <view v-for="file in files" :key="file.id" class="card file" @click="openFile(file)">
      <text class="name">{{ file.fileName }}</text>
      <text class="muted">{{ formatFileSize(file.fileSize) }} · {{ file.fileType }}</text>
      <text v-if="file.folderName" class="muted">{{ file.folderName }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listDriveFiles, type DriveFile } from '../../api/drive';
import { ensureAuthPage } from '../../stores/auth';
import { refreshWorkspaces, workspaceState } from '../../stores/workspace';
import { formatFileSize } from '../../utils/format';
import { getApiBaseUrl } from '../../utils/config';

const files = ref<DriveFile[]>([]);
const loading = ref(false);
const keyword = ref('');
const workspaceIndex = ref(0);

const workspaceNames = computed(() => workspaceState.items.map((w) => w.name));
const currentWorkspace = computed(() => workspaceState.items[workspaceIndex.value]);
const currentWorkspaceName = computed(() => currentWorkspace.value?.name || '');

function onWorkspaceChange(e: { detail: { value: string } }) {
  workspaceIndex.value = Number(e.detail.value);
  load();
}

function resolveFileUrl(url: string) {
  if (url.startsWith('http')) return url;
  const base = getApiBaseUrl().replace(/\/api\/v1$/, '');
  return `${base}${url.startsWith('/') ? url : `/${url}`}`;
}

function openFile(file: DriveFile) {
  const url = resolveFileUrl(file.fileUrl);
  // #ifdef H5
  window.open(url, '_blank');
  // #endif
  // #ifndef H5
  uni.showModal({
    title: file.fileName,
    content: '是否在浏览器中打开？',
    success: (res) => {
      if (res.confirm) plus.runtime.openURL(url);
    },
  });
  // #endif
}

async function load() {
  if (!currentWorkspace.value) return;
  loading.value = true;
  try {
    files.value = await listDriveFiles({
      workspaceId: String(currentWorkspace.value.id),
      keyword: keyword.value.trim() || undefined,
    });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

onShow(async () => {
  if (!ensureAuthPage()) return;
  await refreshWorkspaces();
  load();
});
</script>

<style scoped lang="scss">
.page { padding: 24rpx; }
.toolbar { padding: 20rpx; margin-bottom: 20rpx; }
.picker { color: #0891b2; margin-bottom: 16rpx; }
.search {
  height: 72rpx;
  padding: 0 20rpx;
  background: #fafaf9;
  border-radius: 12rpx;
  border: 1rpx solid #e7e5e4;
}
.file .name { display: block; font-size: 30rpx; font-weight: 600; margin-bottom: 8rpx; }
</style>
