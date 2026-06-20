<template>
  <view class="page-safe">
    <view class="search-box">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索笔记、待办和网盘文件"
        focus
        confirm-type="search"
        @confirm="search"
      />
    </view>

    <view class="filter-grid">
      <picker :range="workspaceOptions" :value="workspaceIndex" @change="onWorkspaceChange">
        <view class="filter-field">{{ workspaceOptions[workspaceIndex] || '全部知识库' }}</view>
      </picker>
      <picker :range="folderOptions" :value="folderIndex" @change="onFolderChange">
        <view class="filter-field">{{ folderOptions[folderIndex] || '全部文件夹' }}</view>
      </picker>
      <picker :range="tagOptions" :value="tagIndex" @change="onTagChange">
        <view class="filter-field">{{ tagOptions[tagIndex] || '全部标签' }}</view>
      </picker>
    </view>

    <view v-if="loading" class="empty">搜索中…</view>
    <view v-else-if="searched && totalResults === 0" class="empty card">无匹配结果</view>
    <view v-else-if="!searched" class="hint muted">输入关键词后回车搜索</view>

    <view v-if="noteResults.length" class="section">
      <text class="section-title">笔记</text>
      <view
        v-for="item in noteResults"
        :key="item.noteId"
        class="card result"
        @click="openNote(item.noteId)"
      >
        <text class="type-label">NOTE</text>
        <text class="title">{{ item.title }}</text>
        <text class="snippet muted">{{ item.snippet }}</text>
        <view v-if="item.tags?.length" class="tag-row">
          <text v-for="tag in item.tags" :key="tag.id" class="tag">{{ tag.name }}</text>
        </view>
      </view>
    </view>

    <view v-if="todoResults.length" class="section">
      <text class="section-title">待办</text>
      <view
        v-for="item in todoResults"
        :key="item.id"
        class="card result"
        @click="openTodo(item)"
      >
        <text class="type-label todo">TODO</text>
        <text class="title">{{ item.title }}</text>
        <text v-if="item.description" class="snippet muted">{{ item.description }}</text>
        <text class="meta muted">
          {{ statusLabel(item.status) }} · {{ priorityLabel(item.priority) }}
          <template v-if="item.dueAt"> · 截止 {{ formatDateTime(item.dueAt) }}</template>
        </text>
      </view>
    </view>

    <view v-if="fileResults.length" class="section">
      <text class="section-title">网盘文件</text>
      <view
        v-for="item in fileResults"
        :key="item.id"
        class="card result"
        @click="openFile(item)"
      >
        <text class="type-label file">FILE</text>
        <text class="title">{{ item.fileName }}</text>
        <text class="meta muted">
          {{ formatFileSize(item.fileSize) }}
          <template v-if="item.folderName"> · {{ item.folderName }}</template>
        </text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { listDriveFiles, type DriveFile } from '../../api/drive';
import { listFolders, type Folder } from '../../api/folders';
import { searchNotes, type SearchResult } from '../../api/search';
import { listTags, type Tag } from '../../api/tags';
import {
  listTodos,
  TODO_PRIORITY_LABEL,
  TODO_STATUS_LABEL,
  type TodoItem,
} from '../../api/todos';
import { ensureAuthPage } from '../../stores/auth';
import { refreshWorkspaces, workspaceState } from '../../stores/workspace';
import { formatDateTime, formatFileSize } from '../../utils/format';
import { openExternalUrl } from '../../utils/openFile';

const keyword = ref('');
const noteResults = ref<SearchResult[]>([]);
const todoResults = ref<TodoItem[]>([]);
const fileResults = ref<DriveFile[]>([]);
const loading = ref(false);
const searched = ref(false);
const folders = ref<Folder[]>([]);
const tags = ref<Tag[]>([]);
const workspaceIndex = ref(0);
const folderIndex = ref(0);
const tagIndex = ref(0);

const workspaceOptions = computed(() => ['全部知识库', ...workspaceState.items.map((item) => item.name)]);
const selectedWorkspace = computed(() => workspaceIndex.value > 0 ? workspaceState.items[workspaceIndex.value - 1] : null);
const folderItems = computed(() => selectedWorkspace.value ? folders.value : []);
const tagItems = computed(() => selectedWorkspace.value ? tags.value : []);
const folderOptions = computed(() => ['全部文件夹', ...folderItems.value.map((item) => item.name)]);
const tagOptions = computed(() => ['全部标签', ...tagItems.value.map((item) => item.name)]);
const totalResults = computed(() => noteResults.value.length + todoResults.value.length + fileResults.value.length);

async function onWorkspaceChange(e: { detail: { value: string | number } }) {
  workspaceIndex.value = Number(e.detail.value);
  folderIndex.value = 0;
  tagIndex.value = 0;
  await loadFilterData();
  if (keyword.value.trim()) await search();
}

async function onFolderChange(e: { detail: { value: string | number } }) {
  folderIndex.value = Number(e.detail.value);
  if (keyword.value.trim()) await search();
}

async function onTagChange(e: { detail: { value: string | number } }) {
  tagIndex.value = Number(e.detail.value);
  if (keyword.value.trim()) await search();
}

async function loadFilterData() {
  if (!selectedWorkspace.value) {
    folders.value = [];
    tags.value = [];
    return;
  }
  try {
    const workspaceId = String(selectedWorkspace.value.id);
    const [folderData, tagData] = await Promise.all([
      listFolders(workspaceId),
      listTags(workspaceId),
    ]);
    folders.value = folderData;
    tags.value = tagData;
  } catch {
    folders.value = [];
    tags.value = [];
  }
}

async function search() {
  if (!keyword.value.trim()) return;
  loading.value = true;
  searched.value = true;
  try {
    const workspaceId = selectedWorkspace.value ? String(selectedWorkspace.value.id) : null;
    const q = keyword.value.trim();
    const [notes, todos, files] = await Promise.all([
      searchNotes({
        keyword: q,
        size: 30,
        workspaceId,
        folderId: folderIndex.value > 0 ? folderItems.value[folderIndex.value - 1]?.id : null,
        tagId: tagIndex.value > 0 ? tagItems.value[tagIndex.value - 1]?.id : null,
      }),
      listTodos({
        keyword: q,
        size: 20,
        workspaceId,
      }),
      workspaceId
        ? listDriveFiles({
          workspaceId,
          keyword: q,
        })
        : Promise.resolve([] as DriveFile[]),
    ]);
    noteResults.value = notes.records;
    todoResults.value = todos.records || [];
    fileResults.value = files;
  } catch (e: any) {
    uni.showToast({ title: e?.message || '搜索失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

function statusLabel(status: number) {
  return TODO_STATUS_LABEL[status] || '未知状态';
}

function priorityLabel(priority?: number | null) {
  return TODO_PRIORITY_LABEL[priority || 2] || '中';
}

function openFile(file: DriveFile) {
  openExternalUrl(file.fileUrl, file.fileName);
}

function openTodo(item: TodoItem) {
  uni.showActionSheet({
    itemList: ['打开关联笔记', '前往待办页'],
    success: (res) => {
      if (res.tapIndex === 0) {
        if (item.noteId) {
          openNote(item.noteId);
        } else {
          uni.showToast({ title: '该待办没有关联笔记', icon: 'none' });
        }
      }
      if (res.tapIndex === 1) {
        uni.switchTab({ url: '/pages/todos/todos' });
      }
    },
  });
}

function openNote(id: string) {
  uni.navigateTo({ url: `/pages/notes/detail?id=${id}` });
}

onLoad((query) => {
  ensureAuthPage();
  if (typeof query?.q === 'string') {
    keyword.value = query.q;
  }
});

onShow(async () => {
  if (!ensureAuthPage()) return;
  await refreshWorkspaces();
  await loadFilterData();
  if (keyword.value.trim() && !searched.value) await search();
});
</script>

<style scoped lang="scss">
.search-box { margin-bottom: 24rpx; }
.search-input {
  height: 88rpx;
  padding: 0 28rpx;
  background: #fff;
  border-radius: 20rpx;
  border: 1rpx solid #e7e5e4;
  font-size: 30rpx;
}
.filter-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12rpx;
  margin-bottom: 24rpx;
}
.filter-field {
  min-height: 72rpx;
  line-height: 72rpx;
  padding: 0 22rpx;
  border-radius: 16rpx;
  background: #fff;
  border: 1rpx solid #e7e5e4;
  color: #57534e;
  font-size: 26rpx;
}
.hint { text-align: center; padding: 40rpx; }
.section {
  margin-top: 24rpx;
}
.section-title {
  display: block;
  margin: 0 4rpx 12rpx;
  color: #57534e;
  font-size: 24rpx;
  font-weight: 700;
}
.result {
  position: relative;
}
.type-label {
  display: inline-block;
  margin-bottom: 10rpx;
  padding: 4rpx 10rpx;
  border-radius: 999rpx;
  background: #ecfeff;
  color: #0e7490;
  font-size: 20rpx;
  font-weight: 700;
}
.type-label.todo {
  background: #f0fdf4;
  color: #15803d;
}
.type-label.file {
  background: #fff7ed;
  color: #c2410c;
}
.result .title { display: block; font-size: 32rpx; font-weight: 600; margin-bottom: 8rpx; }
.snippet { display: block; line-height: 1.5; }
.meta { display: block; line-height: 1.5; }
.tag-row { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 14rpx; }
.tag {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: #ecfeff;
  color: #0891b2;
  font-size: 22rpx;
}
</style>
