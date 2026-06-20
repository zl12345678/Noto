<template>
  <view class="notes-page page-safe">
    <view v-if="!workspaceId" class="hub">
      <view class="hub-hero">
        <view>
          <text class="eyebrow">Knowledge</text>
          <text class="hero-title">笔记库</text>
        </view>
        <view class="hero-count">
          <text class="count-num">{{ workspaceState.items.length || '-' }}</text>
          <text class="count-label">知识库</text>
        </view>
      </view>

      <view v-if="loadingWs" class="skeleton-list">
        <view class="skeleton-card" />
        <view class="skeleton-card short" />
      </view>
      <view
        v-for="ws in workspaceState.items"
        :key="ws.id"
        class="workspace-card"
        @click="enterWorkspace(ws.id)"
      >
        <view class="ws-mark">{{ ws.name.slice(0, 1) }}</view>
        <view class="ws-content">
          <text class="ws-name">{{ ws.name }}</text>
          <text class="ws-desc">{{ ws.description || '暂无描述' }}</text>
        </view>
        <text class="ws-arrow">›</text>
      </view>
    </view>

    <view v-else class="note-list-view">
      <view class="list-header">
        <view class="header-top">
          <text class="back" @click="backToHub">‹ 知识库</text>
          <view class="header-actions">
            <text class="create-action" @click="createRootNote">新建</text>
            <text class="create-action secondary" @click="openListActions">更多</text>
          </view>
        </view>
        <text class="workspace-title">{{ currentWorkspaceName }}</text>
        <view class="header-meta">
          <text>{{ total }} 篇文档</text>
          <text>{{ favoriteCount }} 个收藏</text>
        </view>
      </view>

      <view class="search-row">
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索标题或摘要"
          confirm-type="search"
          @confirm="load(true)"
        />
        <text v-if="keyword" class="clear-search" @click="clearSearch">清除</text>
        <text v-else class="search-button" @click="load(true)">搜索</text>
      </view>

      <view v-if="keyword" class="search-mode-hint">
        <text>搜索结果</text>
        <text class="clear-search" @click="clearSearch">退出搜索</text>
      </view>

      <view v-if="loading" class="skeleton-list">
        <view class="skeleton-card" />
        <view class="skeleton-card" />
        <view class="skeleton-card short" />
      </view>
      <view v-else-if="!treeRows.length" class="empty-state">
        <text class="empty-title">{{ keyword ? '没有匹配文档' : '暂无文档' }}</text>
        <text class="empty-desc">{{ keyword ? '换个关键词再试试' : '先创建第一篇笔记' }}</text>
        <button class="empty-button" @click="handleEmptyAction">
          {{ keyword ? '清除搜索' : '新建文档' }}
        </button>
      </view>
      <view v-for="row in treeRows" :key="row.key" class="tree-row" :style="{ paddingLeft: `${24 + row.depth * 28}rpx` }">
        <template v-if="row.kind === 'folder'">
          <view class="tree-folder" @click="toggleFolder(row.id)">
            <text class="tree-chevron">{{ row.expanded ? '⌄' : '›' }}</text>
            <view class="folder-icon">▰</view>
            <view class="tree-main">
              <text class="tree-title">{{ row.name }}</text>
              <text class="tree-meta">{{ row.count }} 项</text>
            </view>
            <text class="folder-create" @click.stop="createNote(row.id)">+</text>
            <text class="row-more" @click.stop="openFolderActions(row.id)">⋯</text>
          </view>
        </template>
        <view v-else class="tree-note" @click="openNote(row.note.id)">
          <text class="tree-spacer">{{ row.hasChildren ? (row.expanded ? '⌄' : '›') : '' }}</text>
          <view class="note-icon">文</view>
          <view class="tree-main">
            <view class="tree-note-head">
              <text class="tree-title">{{ row.note.title || '无标题' }}</text>
              <text v-if="row.note.isFavorite" class="fav">★</text>
            </view>
            <text class="note-excerpt">{{ row.note.excerpt || row.note.summary || contentPreview(row.note) }}</text>
            <view class="note-meta">
              <text>{{ timeLabel(row.note) }}</text>
              <text>{{ contentSize(row.note) }} 字</text>
            </view>
          </view>
          <text class="row-more" @click.stop="openNoteActions(row.note)">⋯</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import {
  createNote as createNoteApi,
  deleteNote,
  listNotes,
  patchNoteStatus,
  patchNoteTree,
  type Note,
} from '../../api/notes';
import { createFolder, deleteFolder, listFolders, updateFolder, type Folder } from '../../api/folders';
import { ensureAuthPage } from '../../stores/auth';
import { refreshWorkspaces, workspaceState } from '../../stores/workspace';
import { formatDateTime } from '../../utils/format';

const workspaceId = ref('');
const notes = ref<Note[]>([]);
const folders = ref<Folder[]>([]);
const keyword = ref('');
const loading = ref(false);
const loadingWs = ref(false);
const total = ref(0);
const expandedKeys = ref<Set<string>>(new Set());

const currentWorkspaceName = computed(() => (
  workspaceState.items.find((w) => String(w.id) === workspaceId.value)?.name || '笔记'
));

const favoriteCount = computed(() => notes.value.filter((note) => note.isFavorite).length);

type TreeRow =
  | { kind: 'folder'; key: string; id: string; name: string; depth: number; expanded: boolean; count: number }
  | { kind: 'note'; key: string; note: Note; depth: number; expanded: boolean; hasChildren: boolean };

const treeRows = computed<TreeRow[]>(() => {
  if (keyword.value.trim()) {
    return notes.value.map((note) => ({
      kind: 'note' as const,
      key: `note:${note.id}`,
      note,
      depth: 0,
      expanded: false,
      hasChildren: false,
    }));
  }

  const rows: TreeRow[] = [];
  const foldersByParent = new Map<string, Folder[]>();
  folders.value.forEach((folder) => {
    const key = folder.parentId ?? 'root';
    foldersByParent.set(key, [...(foldersByParent.get(key) || []), folder]);
  });
  foldersByParent.forEach((items) => items.sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'zh-CN')));

  const notesByFolder = new Map<string, Note[]>();
  const notesByParent = new Map<string, Note[]>();
  notes.value.forEach((note) => {
    if (note.parentId) {
      notesByParent.set(note.parentId, [...(notesByParent.get(note.parentId) || []), note]);
      return;
    }
    const key = note.folderId ?? 'root';
    notesByFolder.set(key, [...(notesByFolder.get(key) || []), note]);
  });
  notesByFolder.forEach((items) => items.sort(sortNotes));
  notesByParent.forEach((items) => items.sort(sortNotes));

  const pushNotes = (folderKey: string, depth: number) => {
    (notesByFolder.get(folderKey) || []).forEach((note) => pushNote(note, depth));
  };

  const pushNote = (note: Note, depth: number) => {
    const children = notesByParent.get(note.id) || [];
    const key = `note:${note.id}`;
    const expanded = expandedKeys.value.has(key);
    rows.push({ kind: 'note', key, note, depth, expanded, hasChildren: children.length > 0 });
    if (expanded) {
      children.forEach((child) => pushNote(child, depth + 1));
    }
  };

  const walkFolders = (parentKey: string, depth: number) => {
    (foldersByParent.get(parentKey) || []).forEach((folder) => {
      const key = `folder:${folder.id}`;
      const expanded = expandedKeys.value.has(key);
      const count = (foldersByParent.get(folder.id) || []).length + (notesByFolder.get(folder.id) || []).length;
      rows.push({ kind: 'folder', key, id: folder.id, name: folder.name, depth, expanded, count });
      if (expanded) {
        walkFolders(folder.id, depth + 1);
        pushNotes(folder.id, depth + 1);
      }
    });
  };

  walkFolders('root', 0);
  pushNotes('root', 0);
  return rows;
});

function sortNotes(a: Note, b: Note) {
  return (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.title || '').localeCompare(b.title || '', 'zh-CN');
}

function enterWorkspace(id: string) {
  workspaceId.value = String(id);
  keyword.value = '';
  uni.setNavigationBarTitle({ title: currentWorkspaceName.value });
  load(true);
}

function backToHub() {
  workspaceId.value = '';
  notes.value = [];
  folders.value = [];
  total.value = 0;
  expandedKeys.value = new Set();
  uni.setNavigationBarTitle({ title: '笔记' });
}

async function load(reset = true) {
  if (!workspaceId.value) return;
  loading.value = true;
  try {
    const [folderData, data] = await Promise.all([
      listFolders(workspaceId.value),
      listNotes({
        page: 1,
        size: 200,
        workspaceId: workspaceId.value,
        keyword: keyword.value.trim() || undefined,
      }),
    ]);
    folders.value = folderData;
    notes.value = reset ? data.records : data.records;
    total.value = data.total;
    if (expandedKeys.value.size === 0 && !keyword.value.trim()) {
      expandedKeys.value = new Set(folderData.filter((folder) => !folder.parentId).map((folder) => `folder:${folder.id}`));
    }
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

function openNote(id: string) {
  uni.navigateTo({ url: `/pages/notes/detail?id=${id}` });
}

function clearSearch() {
  keyword.value = '';
  load(true);
}

function createRootNote() {
  void createNote();
}

function createRootFolder() {
  void createFolderUnder(null);
}

function openImportPage(folderId?: string | null) {
  if (!workspaceId.value) return;
  const params = [`workspaceId=${encodeURIComponent(workspaceId.value)}`];
  if (folderId) params.push(`folderId=${encodeURIComponent(folderId)}`);
  uni.navigateTo({ url: `/pages/notes/import?${params.join('&')}` });
}

function openListActions() {
  uni.showActionSheet({
    itemList: ['新建文件夹', '导入文档'],
    success: (res) => {
      if (res.tapIndex === 0) createRootFolder();
      if (res.tapIndex === 1) openImportPage();
    },
  });
}

function handleEmptyAction() {
  if (keyword.value) {
    clearSearch();
    return;
  }
  void createNote();
}

function toggleFolder(id: string) {
  toggleKey(`folder:${id}`);
}

function toggleKey(key: string) {
  const next = new Set(expandedKeys.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  expandedKeys.value = next;
}

function contentPreview(note: Note) {
  const raw = (note.content || '').replace(/[#>*_`[\]()-]/g, '').replace(/\s+/g, ' ').trim();
  return raw ? raw.slice(0, 72) : '空文档';
}

function contentSize(note: Note) {
  return (note.content || '').replace(/\s/g, '').length;
}

function timeLabel(note: Note) {
  return formatDateTime(note.lastEditedAt || note.updatedAt) || '未编辑';
}

async function createNote(folderId?: string | null) {
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
          folderId: folderId || null,
        });
        if (folderId) {
          expandedKeys.value = new Set([...expandedKeys.value, `folder:${folderId}`]);
        }
        uni.navigateTo({ url: `/pages/notes/detail?id=${note.id}` });
      }
    },
  });
}

async function createFolderUnder(parentId?: string | null) {
  if (!workspaceId.value) return;
  uni.showModal({
    title: parentId ? '新建子文件夹' : '新建文件夹',
    editable: true,
    placeholderText: '文件夹名称',
    success: async (res) => {
      if (!res.confirm || !res.content?.trim()) return;
      try {
        const folder = await createFolder({
          workspaceId: workspaceId.value,
          parentId: parentId || null,
          name: res.content.trim(),
        });
        if (parentId) {
          expandedKeys.value = new Set([...expandedKeys.value, `folder:${parentId}`]);
        } else {
          expandedKeys.value = new Set([...expandedKeys.value, `folder:${folder.id}`]);
        }
        await load(true);
        uni.showToast({ title: '文件夹已创建', icon: 'success' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '创建失败', icon: 'none' });
      }
    },
  });
}

function openFolderActions(folderId: string) {
  const folder = folders.value.find((item) => item.id === folderId);
  if (!folder) return;
  uni.showActionSheet({
    itemList: ['新建子文件夹', '导入到此处', '重命名', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) await createFolderUnder(folderId);
      if (res.tapIndex === 1) openImportPage(folderId);
      if (res.tapIndex === 2) renameFolder(folder);
      if (res.tapIndex === 3) confirmDeleteFolder(folder);
    },
  });
}

function renameFolder(folder: Folder) {
  uni.showModal({
    title: '重命名文件夹',
    editable: true,
    placeholderText: '文件夹名称',
    content: folder.name,
    success: async (res) => {
      if (!res.confirm || !res.content?.trim()) return;
      try {
        await updateFolder(folder.id, {
          name: res.content.trim(),
          parentId: folder.parentId ?? null,
          sortOrder: folder.sortOrder,
        });
        await load(true);
        uni.showToast({ title: '已重命名', icon: 'success' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '重命名失败', icon: 'none' });
      }
    },
  });
}

function confirmDeleteFolder(folder: Folder) {
  uni.showModal({
    title: '删除文件夹',
    content: `确定删除「${folder.name}」吗？其中的文档会保留并移出文件夹。`,
    confirmColor: '#b91c1c',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await deleteFolder(folder.id);
        const next = new Set(expandedKeys.value);
        next.delete(`folder:${folder.id}`);
        expandedKeys.value = next;
        await load(true);
        uni.showToast({ title: '文件夹已删除', icon: 'none' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '删除失败', icon: 'none' });
      }
    },
  });
}

function openNoteActions(item: Note) {
  const archived = item.status === 1;
  uni.showActionSheet({
    itemList: ['移动到文件夹', archived ? '取消归档' : '归档', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) moveNote(item);
      if (res.tapIndex === 1) await toggleArchive(item);
      if (res.tapIndex === 2) confirmDeleteNote(item);
    },
  });
}

function moveNote(item: Note) {
  const targets = [{ id: null as string | null, name: '根目录' }, ...folders.value.map((folder) => ({
    id: folder.id,
    name: folderPath(folder.id),
  }))];
  uni.showActionSheet({
    itemList: targets.map((target) => target.name).slice(0, 20),
    success: async (res) => {
      const target = targets[res.tapIndex];
      if (!target) return;
      try {
        await patchNoteTree(item.id, {
          folderId: target.id,
          parentId: null,
          sortOrder: nextNoteSortOrder(target.id),
        });
        if (target.id) {
          expandedKeys.value = new Set([...expandedKeys.value, `folder:${target.id}`]);
        }
        await load(true);
        uni.showToast({ title: '已移动', icon: 'success' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '移动失败', icon: 'none' });
      }
    },
  });
}

async function toggleArchive(item: Note) {
  try {
    const updated = await patchNoteStatus(item.id, item.status === 1 ? 0 : 1);
    await load(true);
    uni.showToast({ title: updated.status === 1 ? '已归档' : '已取消归档', icon: 'none' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '操作失败', icon: 'none' });
  }
}

function confirmDeleteNote(item: Note) {
  uni.showModal({
    title: '删除文档',
    content: `确定删除「${item.title || '无标题'}」吗？`,
    confirmColor: '#b91c1c',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await deleteNote(item.id);
        await load(true);
        uni.showToast({ title: '已删除', icon: 'none' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || '删除失败', icon: 'none' });
      }
    },
  });
}

function folderPath(folderId: string) {
  const byId = new Map(folders.value.map((folder) => [folder.id, folder]));
  const names: string[] = [];
  let current = byId.get(folderId);
  const guard = new Set<string>();
  while (current && !guard.has(current.id)) {
    guard.add(current.id);
    names.unshift(current.name);
    current = current.parentId ? byId.get(current.parentId) : undefined;
  }
  return names.join(' / ') || '未命名文件夹';
}

function nextNoteSortOrder(folderId: string | null) {
  const values = notes.value
    .filter((note) => (note.folderId ?? null) === folderId && !note.parentId)
    .map((note) => note.sortOrder ?? 0);
  return (values.length ? Math.max(...values) : 0) + 1;
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
.notes-page {
  background:
    linear-gradient(180deg, #f6f3ee 0%, #f8f7f4 42%, #f7f6f3 100%);
}

.hub-hero {
  min-height: 176rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx;
  margin-bottom: 28rpx;
  border-radius: 28rpx;
  background: #17201f;
  color: #fff;
  box-shadow: 0 20rpx 44rpx rgba(23, 32, 31, 0.16);
}

.eyebrow {
  display: block;
  color: rgba(255, 255, 255, 0.56);
  font-size: 22rpx;
  font-weight: 600;
  margin-bottom: 10rpx;
}

.hero-title {
  display: block;
  font-size: 52rpx;
  font-weight: 750;
  line-height: 1.05;
}

.hero-count {
  width: 132rpx;
  height: 132rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.count-num {
  font-size: 40rpx;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}

.count-label {
  margin-top: 6rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 22rpx;
}

.workspace-card {
  display: flex;
  align-items: center;
  gap: 22rpx;
  padding: 28rpx 26rpx;
  margin-bottom: 18rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 10rpx 28rpx rgba(68, 64, 60, 0.06);
}

.ws-mark {
  width: 76rpx;
  height: 76rpx;
  border-radius: 20rpx;
  background: #e0f2f1;
  color: #0f766e;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  font-weight: 700;
}

.ws-content {
  flex: 1;
  min-width: 0;
}

.ws-name {
  display: block;
  font-size: 34rpx;
  font-weight: 650;
  color: #1c1917;
  margin-bottom: 8rpx;
}

.ws-desc {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: #78716c;
  font-size: 25rpx;
}

.ws-arrow {
  color: #a8a29e;
  font-size: 40rpx;
}

.list-header {
  padding: 28rpx;
  margin-bottom: 22rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 12rpx 32rpx rgba(68, 64, 60, 0.07);
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.back {
  color: #0f766e;
  font-size: 28rpx;
  font-weight: 600;
}

.create-action {
  min-width: 112rpx;
  height: 58rpx;
  line-height: 58rpx;
  text-align: center;
  border-radius: 16rpx;
  background: #0891b2;
  color: #fff;
  font-size: 26rpx;
  font-weight: 650;
}

.create-action.secondary {
  background: #ecfeff;
  color: #0f766e;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.workspace-title {
  display: block;
  color: #1c1917;
  font-size: 46rpx;
  line-height: 1.16;
  font-weight: 750;
  margin-bottom: 18rpx;
}

.header-meta {
  display: flex;
  gap: 18rpx;
  color: #78716c;
  font-size: 24rpx;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-height: 84rpx;
  padding: 0 20rpx 0 26rpx;
  margin-bottom: 22rpx;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: inset 0 0 0 1rpx #ebe7df;
}

.search-input {
  flex: 1;
  height: 84rpx;
  font-size: 28rpx;
}

.search-button,
.clear-search {
  color: #0891b2;
  font-size: 26rpx;
  font-weight: 650;
}

.search-mode-hint {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
  color: #78716c;
  font-size: 25rpx;
}

.tree-row {
  margin-bottom: 12rpx;
  box-sizing: border-box;
}

.tree-folder,
.tree-note {
  display: flex;
  align-items: center;
  gap: 14rpx;
  min-height: 104rpx;
  padding: 18rpx 20rpx;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 8rpx 24rpx rgba(68, 64, 60, 0.05);
}

.tree-note {
  align-items: flex-start;
}

.tree-chevron,
.tree-spacer {
  width: 26rpx;
  color: #a8a29e;
  font-size: 32rpx;
  line-height: 52rpx;
}

.folder-icon,
.note-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 24rpx;
  font-weight: 700;
}

.folder-icon {
  background: #fef3c7;
  color: #b45309;
}

.note-icon {
  background: #e0f2fe;
  color: #0369a1;
}

.tree-main {
  flex: 1;
  min-width: 0;
}

.tree-note-head {
  display: flex;
  gap: 12rpx;
  align-items: flex-start;
}

.tree-title {
  flex: 1;
  min-width: 0;
  color: #1c1917;
  font-size: 31rpx;
  font-weight: 700;
  line-height: 1.32;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tree-meta {
  display: block;
  margin-top: 6rpx;
  color: #a8a29e;
  font-size: 23rpx;
}

.folder-create {
  width: 52rpx;
  height: 52rpx;
  border-radius: 16rpx;
  background: #ecfeff;
  color: #0891b2;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
}

.row-more {
  flex: 0 0 auto;
  width: 52rpx;
  height: 52rpx;
  line-height: 42rpx;
  border-radius: 16rpx;
  background: #f5f5f4;
  color: #78716c;
  text-align: center;
  font-size: 34rpx;
  font-weight: 700;
}

.note-excerpt {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-top: 14rpx;
  color: #57534e;
  font-size: 27rpx;
  line-height: 1.55;
}

.note-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 18rpx;
  color: #a8a29e;
  font-size: 23rpx;
}

.fav {
  width: 40rpx;
  height: 40rpx;
  border-radius: 14rpx;
  background: #fff7ed;
  color: #d97706;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
}

.empty-state {
  padding: 72rpx 36rpx;
  border-radius: 28rpx;
  background: #fff;
  text-align: center;
  box-shadow: 0 10rpx 28rpx rgba(68, 64, 60, 0.05);
}

.empty-title {
  display: block;
  color: #1c1917;
  font-size: 34rpx;
  font-weight: 700;
  margin-bottom: 10rpx;
}

.empty-desc {
  display: block;
  color: #78716c;
  font-size: 26rpx;
  margin-bottom: 28rpx;
}

.empty-button {
  display: inline-block;
  min-width: 180rpx;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 28rpx;
  border-radius: 18rpx;
  background: #17201f;
  color: #fff;
  font-size: 27rpx;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.skeleton-card {
  height: 142rpx;
  border-radius: 24rpx;
  background: linear-gradient(90deg, #ede9e2 0%, #f6f3ee 48%, #ede9e2 100%);
}

.skeleton-card.short {
  height: 108rpx;
}
</style>
