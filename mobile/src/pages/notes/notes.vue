<template>
  <view class="page-safe">
    <view v-if="!workspaceId" class="hub">
      <text class="hub-desc muted">选择知识库开始阅读或轻量编辑</text>
      <view v-if="loadingWs" class="empty">加载中…</view>
      <view
        v-for="ws in workspaceState.items"
        :key="ws.id"
        class="card ws-card list-row"
        @click="enterWorkspace(ws.id)"
      >
        <view>
          <text class="ws-name">{{ ws.name }}</text>
          <text v-if="ws.description" class="muted">{{ ws.description }}</text>
        </view>
        <text class="muted">›</text>
      </view>
    </view>

    <view v-else>
      <view class="toolbar">
        <text class="back" @click="backToHub">‹ 知识库</text>
        <text class="fab" @click="createNote">+ 新建</text>
      </view>
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索文档标题"
        confirm-type="search"
        @confirm="load(true)"
      />
      <view v-if="loading" class="empty">加载中…</view>
      <view v-else-if="!notes.length" class="empty card">暂无文档</view>
      <view v-for="note in notes" :key="note.id" class="card note-row" @click="openNote(note.id)">
        <view class="note-main">
          <text class="note-title">{{ note.title || '无标题' }}</text>
          <text class="muted note-excerpt">{{ note.excerpt || note.summary || '暂无摘要' }}</text>
        </view>
        <text v-if="note.isFavorite" class="fav">★</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import { createNote as createNoteApi, listNotes, type Note } from '../../api/notes';
import { ensureAuthPage } from '../../stores/auth';
import { refreshWorkspaces, workspaceState } from '../../stores/workspace';

const workspaceId = ref('');
const notes = ref<Note[]>([]);
const keyword = ref('');
const loading = ref(false);
const loadingWs = ref(false);

function enterWorkspace(id: string) {
  workspaceId.value = String(id);
  uni.setNavigationBarTitle({ title: workspaceState.items.find((w) => String(w.id) === String(id))?.name || '笔记' });
  load(true);
}

function backToHub() {
  workspaceId.value = '';
  notes.value = [];
  uni.setNavigationBarTitle({ title: '笔记' });
}

async function load(reset = true) {
  if (!workspaceId.value) return;
  loading.value = true;
  try {
    const data = await listNotes({
      page: 1,
      size: 50,
      workspaceId: workspaceId.value,
      keyword: keyword.value.trim() || undefined,
    });
    notes.value = reset ? data.records : data.records;
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

function openNote(id: string) {
  uni.navigateTo({ url: `/pages/notes/detail?id=${id}` });
}

async function createNote() {
  if (!workspaceId.value) return;
  uni.showModal({
    title: '新建文档',
    editable: true,
    placeholderText: '文档标题',
    success: async (res) => {
      if (res.confirm && res.content?.trim()) {
        const note = await createNoteApi({
          title: res.content.trim(),
          content: '',
          workspaceId: workspaceId.value,
        });
        uni.navigateTo({ url: `/pages/notes/detail?id=${note.id}` });
      }
    },
  });
}

onShow(async () => {
  if (!ensureAuthPage()) return;
  loadingWs.value = true;
  try {
    await refreshWorkspaces();
    if (workspaceState.items.length === 1 && !workspaceId.value) {
      enterWorkspace(String(workspaceState.items[0].id));
    }
  } finally {
    loadingWs.value = false;
  }
});

onPullDownRefresh(async () => {
  if (workspaceId.value) await load(true);
  else await refreshWorkspaces();
  uni.stopPullDownRefresh();
});
</script>

<style scoped lang="scss">
.hub-desc { display: block; margin-bottom: 20rpx; }
.ws-name { display: block; font-size: 32rpx; font-weight: 600; }
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.back { color: #0891b2; font-size: 30rpx; }
.fab { color: #0891b2; font-size: 30rpx; font-weight: 600; }
.search-input {
  height: 80rpx;
  padding: 0 24rpx;
  margin-bottom: 20rpx;
  background: #fff;
  border-radius: 16rpx;
  border: 1rpx solid #e7e5e4;
}
.note-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}
.note-title { display: block; font-size: 32rpx; font-weight: 600; margin-bottom: 8rpx; }
.note-excerpt {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.fav { color: #d97706; font-size: 32rpx; padding-top: 4rpx; }
</style>
