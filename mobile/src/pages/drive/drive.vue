<template>
  <view class="drive-page page-safe">
    <view class="explorer-head">
      <view class="head-top">
        <view>
          <text class="eyebrow">Files</text>
          <text class="page-title">网盘</text>
        </view>
        <picker :range="workspaceNames" :value="workspaceIndex" @change="onWorkspaceChange">
          <view class="workspace-picker">{{ currentWorkspaceName || '知识库' }} ▾</view>
        </picker>
      </view>
      <input v-model="keyword" class="search" placeholder="搜索文件或文件夹" confirm-type="search" @confirm="loadAll" />
      <view class="head-actions">
        <button class="head-btn" :loading="uploading" @click="chooseAndUpload">上传</button>
        <button class="head-btn subtle" @click="openDrivePageActions">更多</button>
      </view>
    </view>

    <view v-if="selectionMode" class="selection-bar">
      <text class="selection-count">已选 {{ selectedKeys.length }} 个项目</text>
      <view class="selection-actions">
        <button class="selection-btn" @click="selectCurrentFiles">全选当前</button>
        <button class="selection-btn" :loading="batchDownloading" :disabled="!selectedKeys.length" @click="downloadSelectedFiles">
          批量下载
        </button>
        <button class="selection-btn primary" :loading="batchSharing" :disabled="!selectedKeys.length" @click="shareSelectedFiles">
          批量分享
        </button>
      </view>
    </view>

    <view class="explorer-nav">
      <text class="back-btn" :class="{ disabled: !canGoBack }" @click="goBack">‹ 上级</text>
      <scroll-view scroll-x class="breadcrumb" :show-scrollbar="false">
        <text
          v-for="(item, index) in breadcrumbItems"
          :key="item.key"
          class="crumb"
          :class="{ active: index === breadcrumbItems.length - 1 }"
          @click="navigateTo(item.key)"
        >
          {{ item.label }}
        </text>
      </scroll-view>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!explorerItems.length" class="empty-state">
      <text class="empty-title">{{ keyword ? '没有匹配项目' : '此目录为空' }}</text>
      <text class="empty-desc">{{ keyword ? '换个关键词搜索' : '可以上传文件或新建文件夹' }}</text>
    </view>
    <view
      v-for="item in explorerItems"
      :key="item.key"
      class="explorer-item"
      :class="{ selected: isItemSelected(item) }"
      @click="openItem(item)"
      @longpress="enterSelection(item)"
    >
      <text v-if="selectionMode" class="select-mark">
        {{ isItemSelected(item) ? '✓' : '' }}
      </text>
      <view class="item-icon" :class="item.kind">{{ item.kind === 'folder' ? '夹' : fileIcon(item.file) }}</view>
      <view class="item-main">
        <text class="item-name">{{ item.name }}</text>
        <text class="item-meta">{{ itemMeta(item) }}</text>
      </view>
      <text class="item-arrow">{{ item.kind === 'folder' ? '›' : '打开' }}</text>
      <text v-if="!selectionMode" class="item-more" @click.stop="openActions(item)">⋯</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import {
  createDriveFolder,
  batchDownloadDriveSelection,
  deleteDriveFile,
  deleteDriveFolder,
  linkDriveFileToNote,
  listDriveFiles,
  listDriveFolders,
  moveDriveFileToFolder,
  unlinkDriveFileFromNote,
  updateDriveFolder,
  uploadDriveFilePath,
  type DriveFile,
  type DriveFolder,
} from '../../api/drive';
import { listNotes, type Note } from '../../api/notes';
import { ensureAuthPage } from '../../stores/auth';
import { refreshWorkspaces, workspaceState } from '../../stores/workspace';
import { formatFileSize } from '../../utils/format';
import { openExternalUrl } from '../../utils/openFile';
import { pickShareOptions } from '../../utils/shareOptions';

const files = ref<DriveFile[]>([]);
const folders = ref<DriveFolder[]>([]);
const loading = ref(false);
const uploading = ref(false);
const keyword = ref('');
const workspaceIndex = ref(0);
const currentLocationKey = ref('root');
const selectionMode = ref(false);
const selectedKeys = ref<string[]>([]);
const batchDownloading = ref(false);
const batchSharing = ref(false);

const workspaceNames = computed(() => workspaceState.items.map((w) => w.name));
const currentWorkspace = computed(() => workspaceState.items[workspaceIndex.value]);
const currentWorkspaceName = computed(() => currentWorkspace.value?.name || '');

function onWorkspaceChange(e: { detail: { value: string } }) {
  workspaceIndex.value = Number(e.detail.value);
  currentLocationKey.value = 'root';
  loadAll();
}

const currentFolder = computed(() => {
  if (!currentLocationKey.value.startsWith('folder:')) return null;
  const id = currentLocationKey.value.replace('folder:', '');
  return folders.value.find((folder) => folder.id === id) || null;
});

const canGoBack = computed(() => currentLocationKey.value !== 'root');

const breadcrumbItems = computed(() => {
  const items = [{ key: 'root', label: '网盘' }];
  if (currentLocationKey.value === 'uncategorized') {
    items.push({ key: 'uncategorized', label: '未分类' });
    return items;
  }
  if (currentLocationKey.value === 'unlinked') {
    items.push({ key: 'unlinked', label: '未关联文档' });
    return items;
  }
  if (currentLocationKey.value.startsWith('folder:')) {
    const id = currentLocationKey.value.replace('folder:', '');
    getFolderAncestors(id).forEach((folder) => {
      items.push({ key: `folder:${folder.id}`, label: folder.name });
    });
  }
  return items;
});

type ExplorerItem =
  | { kind: 'folder'; key: string; name: string; folder?: DriveFolder; virtual?: 'uncategorized' | 'unlinked'; count: number }
  | { kind: 'file'; key: string; name: string; file: DriveFile };

const explorerItems = computed<ExplorerItem[]>(() => {
  const folderItems = currentFolders.value.map((folder) => ({
    kind: 'folder' as const,
    key: `folder:${folder.id}`,
    name: folder.name,
    folder,
    count: folder.fileCount ?? 0,
  }));
  const fileItems = files.value.map((file) => ({
    kind: 'file' as const,
    key: `file:${file.id}`,
    name: file.fileName,
    file,
  }));
  if (currentLocationKey.value === 'root' && !keyword.value.trim()) {
    const uncategorizedCount = allFilesSnapshot.value.filter((file) => !file.folderId).length;
    const unlinkedCount = allFilesSnapshot.value.filter((file) => !file.linkedNotes?.length).length;
    return [
      ...folderItems,
      { kind: 'folder' as const, key: 'uncategorized', name: '未分类', virtual: 'uncategorized' as const, count: uncategorizedCount },
      { kind: 'folder' as const, key: 'unlinked', name: '未关联文档', virtual: 'unlinked' as const, count: unlinkedCount },
      ...fileItems,
    ];
  }
  return [...folderItems, ...fileItems];
});

const currentFolders = computed(() => {
  const q = keyword.value.trim();
  if (q) {
    return folders.value.filter((folder) => folder.name.toLowerCase().includes(q.toLowerCase()));
  }
  if (currentLocationKey.value === 'uncategorized') return [];
  const parentId = currentLocationKey.value === 'root' ? null : currentLocationKey.value.replace('folder:', '');
  return folders.value
    .filter((folder) => (folder.parentId ?? null) === parentId)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.name.localeCompare(b.name, 'zh-CN'));
});

const allFilesSnapshot = ref<DriveFile[]>([]);

function openFile(file: DriveFile) {
  openExternalUrl(file.fileUrl, file.fileName);
}

function openItem(item: ExplorerItem) {
  if (selectionMode.value) {
    toggleItemSelection(item);
    return;
  }
  if (item.kind === 'file') {
    openFile(item.file);
    return;
  }
  navigateTo(item.key);
}

function enterSelection(item: ExplorerItem) {
  selectionMode.value = true;
  toggleItemSelection(item);
}

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value;
  if (!selectionMode.value) selectedKeys.value = [];
}

function openDrivePageActions() {
  const items = ['新建文件夹', selectionMode.value ? '取消选择' : '选择项目'];
  uni.showActionSheet({
    itemList: items,
    success: (res) => {
      if (res.tapIndex === 0) openCreateFolder();
      if (res.tapIndex === 1) toggleSelectionMode();
    },
  });
}

function selectionKey(item: ExplorerItem) {
  if (item.kind === 'file') return `file:${item.file.id}`;
  if (item.virtual === 'uncategorized') return 'location:uncategorized';
  if (item.virtual === 'unlinked') return 'location:unlinked';
  return item.folder ? `folder:${item.folder.id}` : item.key;
}

function isItemSelected(item: ExplorerItem) {
  return selectedKeys.value.includes(selectionKey(item));
}

function toggleItemSelection(item: ExplorerItem) {
  const key = selectionKey(item);
  if (selectedKeys.value.includes(key)) {
    selectedKeys.value = selectedKeys.value.filter((itemKey) => itemKey !== key);
  } else {
    selectedKeys.value = [...selectedKeys.value, key];
  }
}

function selectCurrentFiles() {
  const visibleKeys = explorerItems.value.map((item) => selectionKey(item));
  const next = new Set([...selectedKeys.value, ...visibleKeys]);
  selectedKeys.value = Array.from(next);
}

function openActions(item: ExplorerItem) {
  if (item.kind === 'folder') {
    openFolderActions(item);
    return;
  }
  openFileActions(item.file);
}

function openFolderActions(item: Extract<ExplorerItem, { kind: 'folder' }>) {
  if (item.virtual) {
    uni.showActionSheet({
      itemList: ['打开'],
      success: () => navigateTo(item.key),
    });
    return;
  }
  if (!item.folder) return;
  uni.showActionSheet({
    itemList: ['打开', '新建子文件夹', '重命名', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) navigateTo(item.key);
      if (res.tapIndex === 1) openCreateFolder(item.folder?.id);
      if (res.tapIndex === 2 && item.folder) openRenameFolder(item.folder);
      if (res.tapIndex === 3 && item.folder) confirmDeleteFolder(item.folder);
    },
  });
}

function openFileActions(file: DriveFile) {
  uni.showActionSheet({
    itemList: ['打开', '分享', '关联到笔记', '查看关联笔记', '移动到文件夹', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) openFile(file);
      if (res.tapIndex === 1) shareFile(file);
      if (res.tapIndex === 2) linkFileToNote(file);
      if (res.tapIndex === 3) openLinkedNotes(file);
      if (res.tapIndex === 4) moveFile(file);
      if (res.tapIndex === 5) confirmDeleteFile(file);
    },
  });
}

function navigateTo(key: string) {
  currentLocationKey.value = key;
  loadFiles();
}

function goBack() {
  if (!canGoBack.value) return;
  if (currentLocationKey.value === 'uncategorized' || currentLocationKey.value === 'unlinked') {
    navigateTo('root');
    return;
  }
  if (currentFolder.value?.parentId) navigateTo(`folder:${currentFolder.value.parentId}`);
  else navigateTo('root');
}

function currentFolderIdForUpload() {
  if (currentLocationKey.value.startsWith('folder:')) return currentLocationKey.value.replace('folder:', '');
  return null;
}

function chooseAndUpload() {
  if (!currentWorkspace.value) {
    uni.showToast({ title: '暂无知识库', icon: 'none' });
    return;
  }
  const chooseFile = (uni as any).chooseFile;
  if (!chooseFile) {
    uni.showToast({ title: '当前平台不支持选择文件', icon: 'none' });
    return;
  }
  chooseFile({
    count: 1,
    success: async (res: any) => {
      const file = res.tempFiles?.[0];
      const filePath = file?.path || res.tempFilePaths?.[0];
      if (!filePath) return;
      uploading.value = true;
      try {
        await uploadDriveFilePath({
          workspaceId: String(currentWorkspace.value.id),
          filePath,
          name: file?.name,
          folderId: currentFolderIdForUpload(),
        });
        uni.showToast({ title: '上传成功', icon: 'success' });
        await loadAll();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '上传失败', icon: 'none' });
      } finally {
        uploading.value = false;
      }
    },
  });
}

function openCreateFolder(parentId?: string | null) {
  if (!currentWorkspace.value) return;
  uni.showModal({
    title: parentId ? '新建子文件夹' : '新建文件夹',
    editable: true,
    placeholderText: '文件夹名称',
    success: async (res) => {
      if (!res.confirm || !res.content?.trim()) return;
      try {
        await createDriveFolder(
          String(currentWorkspace.value.id),
          res.content.trim(),
          parentId === undefined ? currentParentFolderId() : parentId,
        );
        uni.showToast({ title: '文件夹已创建', icon: 'success' });
        await loadAll();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '创建失败', icon: 'none' });
      }
    },
  });
}

function currentParentFolderId() {
  if (currentLocationKey.value.startsWith('folder:')) return currentLocationKey.value.replace('folder:', '');
  return null;
}

function openRenameFolder(folder: DriveFolder) {
  uni.showModal({
    title: '重命名文件夹',
    editable: true,
    placeholderText: '文件夹名称',
    content: folder.name,
    success: async (res) => {
      if (!res.confirm || !res.content?.trim()) return;
      try {
        await updateDriveFolder(folder.id, res.content.trim());
        uni.showToast({ title: '已重命名', icon: 'success' });
        await loadAll();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '重命名失败', icon: 'none' });
      }
    },
  });
}

function confirmDeleteFolder(folder: DriveFolder) {
  uni.showModal({
    title: '删除文件夹',
    content: `确定删除「${folder.name}」吗？其中的文件会移到未分类。`,
    confirmColor: '#b91c1c',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await deleteDriveFolder(folder.id);
        if (currentLocationKey.value === `folder:${folder.id}`) currentLocationKey.value = 'root';
        uni.showToast({ title: '文件夹已删除', icon: 'none' });
        await loadAll();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '删除失败', icon: 'none' });
      }
    },
  });
}

function moveFile(file: DriveFile) {
  const targets = [{ id: null as string | null, name: '未分类' }, ...folders.value.map((folder) => ({
    id: folder.id,
    name: folderPath(folder.id),
  }))];
  uni.showActionSheet({
    itemList: targets.map((item) => item.name).slice(0, 20),
    success: async (res) => {
      const target = targets[res.tapIndex];
      if (!target) return;
      try {
        await moveDriveFileToFolder(file.id, target.id);
        uni.showToast({ title: '已移动', icon: 'success' });
        await loadAll();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '移动失败', icon: 'none' });
      }
    },
  });
}

async function shareFile(file: DriveFile) {
  const options = await pickShareOptions();
  if (options === null) return;
  try {
    const { buildShareUrl, createAttachmentShare } = await import('../../api/share');
    const link = await createAttachmentShare(file.id, options);
    const url = buildShareUrl(link.sharePath);
    uni.setClipboardData({
      data: url,
      success: () => uni.showToast({ title: '分享链接已复制', icon: 'none' }),
    });
  } catch (e: any) {
    uni.showToast({ title: e?.message || '创建分享失败', icon: 'none' });
  }
}

async function shareSelectedFiles() {
  if (!selectedKeys.value.length || !currentWorkspace.value) {
    uni.showToast({ title: '请选择项目', icon: 'none' });
    return;
  }
  const options = await pickShareOptions();
  if (options === null) return;
  batchSharing.value = true;
  try {
    const fileIds = await resolveSelectedFileIds();
    if (!fileIds.length) {
      uni.showToast({ title: '所选项目中没有可分享的文件', icon: 'none' });
      return;
    }
    const { buildShareUrl, createBatchAttachmentShare } = await import('../../api/share');
    const link = await createBatchAttachmentShare({ ids: fileIds, ...options });
    const url = buildShareUrl(link.sharePath);
    uni.setClipboardData({
      data: url,
      success: () => uni.showToast({ title: `已复制 ${link.itemCount || fileIds.length} 个文件的分享链接`, icon: 'none' }),
    });
    selectionMode.value = false;
    selectedKeys.value = [];
  } catch (e: any) {
    uni.showToast({ title: e?.message || '批量分享失败', icon: 'none' });
  } finally {
    batchSharing.value = false;
  }
}

async function downloadSelectedFiles() {
  if (!selectedKeys.value.length || !currentWorkspace.value) {
    uni.showToast({ title: '请选择项目', icon: 'none' });
    return;
  }
  const payload = resolveBatchDownloadPayload();
  if (!payload) return;
  batchDownloading.value = true;
  try {
    await batchDownloadDriveSelection(payload);
    uni.showToast({ title: '下载已开始', icon: 'success' });
    selectionMode.value = false;
    selectedKeys.value = [];
  } catch (e: any) {
    uni.showToast({ title: e?.message || '批量下载失败', icon: 'none' });
  } finally {
    batchDownloading.value = false;
  }
}

function resolveBatchDownloadPayload() {
  if (!currentWorkspace.value || !selectedKeys.value.length) return null;
  const ids: string[] = [];
  const folderIds: string[] = [];
  let uncategorized = false;
  let unlinkedOnly = false;

  selectedKeys.value.forEach((key) => {
    if (key.startsWith('file:')) ids.push(key.replace('file:', ''));
    if (key.startsWith('folder:')) folderIds.push(key.replace('folder:', ''));
    if (key === 'location:uncategorized') uncategorized = true;
    if (key === 'location:unlinked') unlinkedOnly = true;
  });

  return {
    workspaceId: String(currentWorkspace.value.id),
    ids: ids.length ? ids : undefined,
    folderIds: folderIds.length ? folderIds : undefined,
    uncategorized: uncategorized || undefined,
    unlinkedOnly: unlinkedOnly || undefined,
  };
}

async function resolveSelectedFileIds() {
  if (!currentWorkspace.value) return [];
  const payload = resolveBatchDownloadPayload();
  if (!payload) return [];

  const ids = [...(payload.ids || [])];
  if (payload.folderIds?.length) {
    for (const folderId of payload.folderIds) {
      const scopedIds = [folderId, ...getFolderDescendantIds(folderId)];
      for (const scopedFolderId of scopedIds) {
        const folderFiles = await listDriveFiles({
          workspaceId: String(currentWorkspace.value.id),
          folderId: scopedFolderId,
        });
        folderFiles.forEach((file) => ids.push(file.id));
      }
    }
  }
  if (payload.uncategorized) {
    const uncategorizedFiles = await listDriveFiles({
      workspaceId: String(currentWorkspace.value.id),
      uncategorized: true,
    });
    uncategorizedFiles.forEach((file) => ids.push(file.id));
  }
  if (payload.unlinkedOnly) {
    const unlinkedFiles = await listDriveFiles({
      workspaceId: String(currentWorkspace.value.id),
      unlinkedOnly: true,
    });
    unlinkedFiles.forEach((file) => ids.push(file.id));
  }
  return [...new Set(ids)];
}

async function linkFileToNote(file: DriveFile) {
  if (!currentWorkspace.value) return;
  uni.showModal({
    title: '搜索笔记',
    editable: true,
    placeholderText: '输入标题关键词，可留空',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        const page = await listNotes({
          workspaceId: String(currentWorkspace.value.id),
          keyword: res.content?.trim() || undefined,
          page: 1,
          size: 50,
        });
        const linkedIds = new Set((file.linkedNotes || []).map((item) => String(item.id)));
        const candidates = page.records.filter((item) => !linkedIds.has(String(item.id)));
        if (!candidates.length) {
          uni.showToast({ title: '没有可关联的笔记', icon: 'none' });
          return;
        }
        chooseNoteToLink(file, candidates);
      } catch (e: any) {
        uni.showToast({ title: e?.message || '加载笔记失败', icon: 'none' });
      }
    },
  });
}

function chooseNoteToLink(file: DriveFile, candidates: Note[]) {
  uni.showActionSheet({
    itemList: candidates.map((item) => item.title || '无标题').slice(0, 20),
    success: async (res) => {
      const note = candidates[res.tapIndex];
      if (!note) return;
      try {
        await linkDriveFileToNote(file.id, note.id);
        uni.showToast({ title: '已关联到笔记', icon: 'success' });
        await loadAll();
      } catch (e: any) {
        uni.showToast({ title: e?.message || '关联失败', icon: 'none' });
      }
    },
  });
}

function openLinkedNotes(file: DriveFile) {
  const linked = file.linkedNotes || [];
  if (!linked.length) {
    uni.showToast({ title: '还没有关联笔记', icon: 'none' });
    return;
  }
  uni.showActionSheet({
    itemList: linked.map((item) => item.title || '无标题').slice(0, 20),
    success: (res) => {
      const note = linked[res.tapIndex];
      if (!note) return;
      uni.showActionSheet({
        itemList: ['打开笔记', '取消关联'],
        success: async (action) => {
          if (action.tapIndex === 0) {
            uni.navigateTo({ url: `/pages/notes/detail?id=${note.id}` });
          }
          if (action.tapIndex === 1) {
            await unlinkFileFromNote(file, String(note.id));
          }
        },
      });
    },
  });
}

async function unlinkFileFromNote(file: DriveFile, noteId: string) {
  try {
    await unlinkDriveFileFromNote(file.id, noteId);
    uni.showToast({ title: '已取消关联', icon: 'none' });
    await loadAll();
  } catch (e: any) {
    uni.showToast({ title: e?.message || '取消关联失败', icon: 'none' });
  }
}

function confirmDeleteFile(file: DriveFile) {
  uni.showModal({
    title: '删除文件',
    content: `确定删除「${file.fileName}」吗？`,
    confirmColor: '#b91c1c',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await deleteDriveFile(file.id);
        uni.showToast({ title: '已删除', icon: 'none' });
        await loadAll();
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

function getFolderAncestors(folderId: string) {
  const byId = new Map(folders.value.map((folder) => [folder.id, folder]));
  const result: DriveFolder[] = [];
  let current = byId.get(folderId);
  const guard = new Set<string>();
  while (current && !guard.has(current.id)) {
    guard.add(current.id);
    result.unshift(current);
    current = current.parentId ? byId.get(current.parentId) : undefined;
  }
  return result;
}

function getFolderDescendantIds(folderId: string) {
  const result: string[] = [];
  const walk = (parentId: string) => {
    folders.value
      .filter((folder) => folder.parentId === parentId)
      .forEach((folder) => {
        result.push(folder.id);
        walk(folder.id);
      });
  };
  walk(folderId);
  return result;
}

function fileIcon(file: DriveFile) {
  const type = (file.fileType || file.fileName).toLowerCase();
  if (type.includes('image') || /\.(png|jpg|jpeg|gif|webp)$/.test(file.fileName)) return '图';
  if (type.includes('pdf') || file.fileName.endsWith('.pdf')) return 'PDF';
  if (/\.(doc|docx)$/.test(file.fileName)) return 'DOC';
  if (/\.(xls|xlsx)$/.test(file.fileName)) return '表';
  return '文';
}

function itemMeta(item: ExplorerItem) {
  if (item.kind === 'folder') return `${item.count || 0} 项`;
  const parts = [formatFileSize(item.file.fileSize)];
  if (item.file.linkedNotes?.length) parts.push(`${item.file.linkedNotes.length} 篇笔记`);
  if (item.file.folderName && keyword.value.trim()) parts.push(item.file.folderName);
  return parts.join(' · ');
}

async function loadAll() {
  if (!currentWorkspace.value) return;
  loading.value = true;
  try {
    folders.value = await listDriveFolders(String(currentWorkspace.value.id), keyword.value.trim() || undefined);
    allFilesSnapshot.value = await listDriveFiles({ workspaceId: String(currentWorkspace.value.id) });
    await loadFiles(false);
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

async function loadFiles(setLoading = true) {
  if (!currentWorkspace.value) return;
  if (setLoading) loading.value = true;
  try {
    const params: Parameters<typeof listDriveFiles>[0] = {
      workspaceId: String(currentWorkspace.value.id),
      keyword: keyword.value.trim() || undefined,
    };
    if (!keyword.value.trim()) {
      if (currentLocationKey.value === 'uncategorized') params.uncategorized = true;
      else if (currentLocationKey.value === 'unlinked') params.unlinkedOnly = true;
      else if (currentLocationKey.value.startsWith('folder:')) params.folderId = currentLocationKey.value.replace('folder:', '');
    }
    files.value = await listDriveFiles(params);
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    if (setLoading) loading.value = false;
  }
}

onShow(async () => {
  if (!ensureAuthPage()) return;
  await refreshWorkspaces();
  loadAll();
});
</script>

<style scoped lang="scss">
.drive-page {
  background: linear-gradient(180deg, #f6f3ee 0%, #f8f7f4 48%, #f7f6f3 100%);
}

.explorer-head {
  padding: 28rpx;
  margin-bottom: 20rpx;
  border-radius: 28rpx;
  background: #17201f;
  color: #fff;
}

.head-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20rpx;
  margin-bottom: 22rpx;
}

.eyebrow {
  display: block;
  color: rgba(255, 255, 255, 0.56);
  font-size: 22rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}

.page-title {
  display: block;
  font-size: 48rpx;
  font-weight: 760;
}

.workspace-picker {
  max-width: 260rpx;
  padding: 12rpx 16rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.1);
  font-size: 25rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.search {
  height: 78rpx;
  padding: 0 22rpx;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 18rpx;
  color: #1c1917;
}

.head-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14rpx;
  margin-top: 16rpx;
}

.head-btn {
  height: 70rpx;
  line-height: 70rpx;
  margin: 0;
  padding: 0;
  border: 1rpx solid rgba(255, 255, 255, 0.24);
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 26rpx;
  font-weight: 650;
}

.head-btn.subtle {
  background: rgba(255, 255, 255, 0.08);
}

.selection-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 18rpx 20rpx;
  margin-bottom: 18rpx;
  border: 1rpx solid #bae6fd;
  border-radius: 20rpx;
  background: #ecfeff;
}

.selection-count {
  flex: 1;
  color: #155e75;
  font-size: 26rpx;
  font-weight: 650;
}

.selection-actions {
  display: flex;
  gap: 10rpx;
}

.selection-btn {
  height: 58rpx;
  line-height: 58rpx;
  margin: 0;
  padding: 0 18rpx;
  border-radius: 14rpx;
  border: 1rpx solid #a5f3fc;
  background: #fff;
  color: #0e7490;
  font-size: 24rpx;
}

.selection-btn.primary {
  border-color: #0891b2;
  background: #0891b2;
  color: #fff;
}

.selection-btn[disabled] {
  opacity: 0.48;
}

.explorer-nav {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 18rpx;
}

.back-btn {
  flex-shrink: 0;
  color: #0f766e;
  font-size: 27rpx;
  font-weight: 650;
}

.back-btn.disabled {
  color: #a8a29e;
}

.breadcrumb {
  flex: 1;
  white-space: nowrap;
}

.crumb {
  display: inline-block;
  margin-right: 10rpx;
  padding: 10rpx 16rpx;
  border-radius: 14rpx;
  background: #fff;
  color: #78716c;
  font-size: 24rpx;
}

.crumb.active {
  background: #ecfeff;
  color: #0891b2;
}

.explorer-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  min-height: 112rpx;
  padding: 20rpx 22rpx;
  margin-bottom: 14rpx;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 8rpx 24rpx rgba(68, 64, 60, 0.05);
}

.explorer-item.selected {
  border: 1rpx solid #67e8f9;
  background: #ecfeff;
}

.select-mark {
  flex: 0 0 auto;
  width: 42rpx;
  height: 42rpx;
  line-height: 42rpx;
  border: 2rpx solid #0891b2;
  border-radius: 50%;
  color: #0891b2;
  text-align: center;
  font-size: 26rpx;
  font-weight: 800;
}

.item-icon {
  width: 62rpx;
  height: 62rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 750;
  background: #e0f2fe;
  color: #0369a1;
}

.item-icon.folder {
  background: #fef3c7;
  color: #b45309;
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-name {
  display: block;
  color: #1c1917;
  font-size: 31rpx;
  font-weight: 680;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.item-meta {
  display: block;
  margin-top: 8rpx;
  color: #78716c;
  font-size: 24rpx;
}

.item-arrow {
  color: #a8a29e;
  font-size: 25rpx;
}

.item-more {
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

.empty-state {
  padding: 72rpx 36rpx;
  border-radius: 28rpx;
  background: #fff;
  text-align: center;
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
}
</style>
