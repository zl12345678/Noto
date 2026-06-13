<template>
  <div class="drive-page noto-page">
    <a-card class="drive-hero" :bordered="false">
      <div class="hero-row">
        <div>
          <p class="noto-page-eyebrow">知识库文件</p>
          <h2 class="noto-page-title">网盘</h2>
          <p class="hero-desc">按文件夹管理文件，按文档筛选关联，并支持插入到正文</p>
        </div>
        <a-upload :show-upload-list="false" :before-upload="handleUpload" :disabled="uploading || !workspaceId">
          <a-button type="primary" size="large" :loading="uploading">上传文件</a-button>
        </a-upload>
      </div>
      <a-space wrap class="drive-filters">
        <a-select
          v-model:value="workspaceId"
          placeholder="选择知识库"
          style="min-width: 220px"
          :options="workspaceOptions"
          @change="onWorkspaceChange"
        />
        <a-select
          v-model:value="filterNoteId"
          allow-clear
          show-search
          placeholder="按关联文档筛选"
          style="min-width: 240px"
          :options="noteOptions"
          :filter-option="filterNoteOption"
          @change="loadFiles"
        />
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索文件名"
          allow-clear
          style="min-width: 220px"
        />
      </a-space>
    </a-card>

    <div class="drive-layout">
      <a-card class="folder-card noto-surface-card" :bordered="false" :loading="foldersLoading">
        <div class="tree-toolbar">
          <div class="tree-head">
            <span class="folder-title">文件夹</span>
            <a-dropdown :disabled="!workspaceId">
              <a-button type="primary" size="small">新建</a-button>
              <template #overlay>
                <a-menu @click="handleCreateMenu">
                  <a-menu-item key="folder">新建文件夹</a-menu-item>
                  <a-menu-item key="subfolder" :disabled="!selectedFolder">新建子文件夹</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </div>
        <div class="tree-body" @contextmenu="onTreeBodyContextMenu">
          <a-menu
            mode="inline"
            :selected-keys="[activeFolderKey]"
            class="folder-menu folder-menu-fixed"
            @click="onFolderMenuClick"
          >
            <a-menu-item key="all">
              <span>全部文件</span>
              <span class="folder-count">{{ counts.all }}</span>
            </a-menu-item>
            <a-menu-item key="uncategorized">
              <span>未分类</span>
              <span class="folder-count">{{ counts.uncategorized }}</span>
            </a-menu-item>
            <a-menu-item key="unlinked">
              <span>未关联文档</span>
              <span class="folder-count">{{ counts.unlinked }}</span>
            </a-menu-item>
          </a-menu>
          <a-tree
            v-if="folderTreeData.length"
            class="folder-tree"
            block-node
            :tree-data="folderTreeData"
            :selected-keys="folderTreeSelectedKeys"
            :expanded-keys="folderExpandedKeys"
            @select="onFolderTreeSelect"
            @expand="onFolderTreeExpand"
            @rightClick="onFolderTreeRightClick"
          />
          <p v-else-if="!foldersLoading && workspaceId" class="folder-empty">右键空白处可新建文件夹</p>
        </div>
      </a-card>

      <a-card class="drive-table-card noto-surface-card" :bordered="false" :loading="loading">
        <div v-if="filteredFiles.length" class="table-toolbar">
          <a-space wrap>
            <span v-if="selectedFileIds.length" class="selection-hint">
              已选 {{ selectedFileIds.length }} 项
            </span>
            <a-button
              :disabled="!selectedFileIds.length"
              :loading="batchDownloading"
              @click="handleBatchDownload"
            >
              批量下载
            </a-button>
            <a-button :disabled="!selectedFileIds.length" @click="batchShareModalOpen = true">
              批量分享
            </a-button>
            <a-button type="link" :disabled="!selectedFileIds.length" @click="clearSelection">
              取消选择
            </a-button>
          </a-space>
        </div>
        <a-table
          v-if="filteredFiles.length"
          :data-source="filteredFiles"
          :columns="columns"
          :pagination="false"
          :row-selection="rowSelection"
          row-key="id"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'fileName'">
              <a :href="record.fileUrl" target="_blank" rel="noopener" class="file-link">
                {{ record.fileName }}
              </a>
            </template>
            <template v-else-if="column.key === 'folderName'">
              {{ record.folderName || '未分类' }}
            </template>
            <template v-else-if="column.key === 'fileSize'">
              {{ formatSize(record.fileSize) }}
            </template>
            <template v-else-if="column.key === 'linkedNotes'">
              <a-space v-if="record.linkedNotes?.length" wrap :size="4">
                <a-tag
                  v-for="note in record.linkedNotes"
                  :key="note.id"
                  class="note-tag"
                  @click="openNote(note.id)"
                >
                  {{ note.title || '未命名文档' }}
                </a-tag>
              </a-space>
              <span v-else class="muted-text">未关联</span>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ formatTime(record.createdAt) }}
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-dropdown :trigger="['click']">
                <a-button type="text" size="small" class="file-row-action" aria-label="文件操作">
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu @click="(event) => onFileAction(record, event)">
                    <a-menu-item key="share">分享</a-menu-item>
                    <a-menu-item key="link">关联文档</a-menu-item>
                    <a-menu-item key="move">移动至…</a-menu-item>
                    <a-menu-divider />
                    <a-menu-item key="delete" danger>删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </template>
          </template>
        </a-table>

        <EmptyState
          v-else-if="workspaceId && !loading"
          title="没有匹配的文件"
          description="切换文件夹或上传新文件；也可在文档附件侧栏从网盘关联"
          preset="search"
        >
          <a-upload :show-upload-list="false" :before-upload="handleUpload" :disabled="uploading">
            <a-button type="primary" :loading="uploading">上传文件</a-button>
          </a-upload>
        </EmptyState>

        <EmptyState
          v-else-if="!workspaceId"
          title="请先选择知识库"
          description="网盘按知识库隔离，每个知识库有独立的文件空间"
          preset="search"
        />
      </a-card>
    </div>

    <DriveMoveFolderModal
      v-model:open="moveModalOpen"
      :attachment-id="moveTargetFile?.id"
      :file-name="moveTargetFile?.fileName"
      :folders="folders"
      :current-folder-id="moveTargetFile?.folderId ?? null"
      :loading="foldersLoading"
      @moved="refreshAll"
    />

    <DriveLinkNoteModal
      v-model:open="linkModalOpen"
      :workspace-id="workspaceId"
      :attachment-id="linkTargetId"
      @linked="refreshAll"
    />

    <ShareLinkModal
      v-model:open="shareModalOpen"
      resource-type="ATTACHMENT"
      :resource-id="shareTargetId"
      title="分享网盘文件"
    />

    <BatchShareModal v-model:open="batchShareModalOpen" :file-ids="selectedFileIds" />

    <a-dropdown
      v-model:open="contextMenu.open"
      :trigger="[]"
      overlay-class-name="tree-context-menu"
    >
      <span class="context-menu-anchor" :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }" />
      <template #overlay>
        <a-menu @click="onContextMenuSelect">
          <template v-for="item in activeContextMenuItems" :key="item.key">
            <a-menu-divider v-if="item.divider" />
            <a-menu-item v-else :key="item.key" :danger="item.danger">{{ item.label }}</a-menu-item>
          </template>
        </a-menu>
      </template>
    </a-dropdown>

    <a-modal
      v-model:open="folderModalOpen"
      :title="folderModalMode === 'create' ? '新建文件夹' : '重命名文件夹'"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="folderSaving"
      @ok="handleFolderModalOk"
    >
      <a-input v-model:value="folderNameInput" placeholder="文件夹名称" />
      <p v-if="folderModalMode === 'create' && createFolderParentId" class="folder-modal-hint">
        将创建于「{{ createFolderParentName }}」下
      </p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { computed, onActivated, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Modal, message } from 'ant-design-vue';
import { MoreOutlined } from '@ant-design/icons-vue';
import EmptyState from '../../components/common/EmptyState.vue';
import DriveLinkNoteModal from '../../components/drive/DriveLinkNoteModal.vue';
import DriveMoveFolderModal from '../../components/drive/DriveMoveFolderModal.vue';
import BatchShareModal from '../../components/share/BatchShareModal.vue';
import ShareLinkModal from '../../components/share/ShareLinkModal.vue';
import { deleteAttachment } from '../../api/attachments';
import { listNotes } from '../../api/notes';
import {
  batchDownloadDriveFiles,
  createDriveFolder,
  deleteDriveFolder,
  listDriveFiles,
  listDriveFolders,
  updateDriveFolder,
  uploadDriveFile,
  type DriveFile,
  type DriveFolder,
} from '../../api/drive';
import { useWorkspaceStore } from '../../store/workspace';
import {
  buildDriveFolderTree,
  collectDriveFolderTreeKeys,
  driveFolderTreeToAntData,
} from '../../utils/driveFolderTree';

const route = useRoute();
const router = useRouter();
const workspaceStore = useWorkspaceStore();

const workspaceId = ref<string>();
const keyword = ref('');
const filterNoteId = ref<string>();
const files = ref<DriveFile[]>([]);
const folders = ref<DriveFolder[]>([]);
const noteOptions = ref<{ label: string; value: string }[]>([]);
const loading = ref(false);
const foldersLoading = ref(false);
const uploading = ref(false);
const batchDownloading = ref(false);
const selectedFileIds = ref<string[]>([]);
const linkModalOpen = ref(false);
const linkTargetId = ref<string>();
const shareModalOpen = ref(false);
const shareTargetId = ref<string>();
const batchShareModalOpen = ref(false);
const moveModalOpen = ref(false);
const moveTargetFile = ref<DriveFile | null>(null);
const activeFolderKey = ref('all');
const counts = ref({ all: 0, uncategorized: 0, unlinked: 0 });

const folderModalOpen = ref(false);
const folderModalMode = ref<'create' | 'rename'>('create');
const folderNameInput = ref('');
const folderSaving = ref(false);
const editingFolderId = ref<string>();
const createFolderParentId = ref<string | null>(null);
const folderExpandedKeys = ref<string[]>([]);

type DriveTreeContextNode =
  | { kind: 'root' }
  | { kind: 'folder'; folder: DriveFolder };

type ContextMenuItem = {
  key: string;
  label?: string;
  divider?: boolean;
  danger?: boolean;
};

const contextMenu = reactive({
  open: false,
  x: 0,
  y: 0,
  node: null as DriveTreeContextNode | null,
});

const folderTreeNodes = computed(() => buildDriveFolderTree(folders.value));
const folderTreeData = computed(() => driveFolderTreeToAntData(folderTreeNodes.value));
const folderTreeSelectedKeys = computed(() =>
  activeFolderKey.value.startsWith('folder:') ? [activeFolderKey.value] : [],
);
const createFolderParentName = computed(() => {
  if (!createFolderParentId.value) return '';
  return folders.value.find((item) => item.id === createFolderParentId.value)?.name ?? '';
});

const workspaceOptions = computed(() =>
  workspaceStore.items.map((item) => ({ label: item.name, value: item.id })),
);

const selectedFolder = computed(() => {
  if (!activeFolderKey.value.startsWith('folder:')) return null;
  const id = activeFolderKey.value.replace('folder:', '');
  return folders.value.find((item) => item.id === id) ?? null;
});

const filteredFiles = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  if (!q) return files.value;
  return files.value.filter((item) => item.fileName.toLowerCase().includes(q));
});

const rowSelection = computed(() => ({
  selectedRowKeys: selectedFileIds.value,
  onChange: (keys: (string | number)[]) => {
    selectedFileIds.value = keys.map(String);
  },
}));

const columns = [
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: '文件夹', key: 'folderName', width: 120 },
  { title: '大小', dataIndex: 'fileSize', key: 'fileSize', width: 90 },
  { title: '关联文档', key: 'linkedNotes' },
  { title: '上传时间', dataIndex: 'createdAt', key: 'createdAt', width: 140 },
  { title: '操作', key: 'actions', width: 72, align: 'center' as const },
];

const filterNoteOption = (input: string, option?: { label?: string; value?: string }) =>
  (option?.label ?? '').toLowerCase().includes(input.toLowerCase());

const formatSize = (bytes: number) => {
  if (!bytes || bytes < 1024) return `${bytes || 0} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const formatTime = (value?: string) => (value ? dayjs(value).format('MM-DD HH:mm') : '—');

const buildFileQuery = () => {
  if (!workspaceId.value) return null;
  const query: {
    workspaceId: string;
    folderId?: string;
    uncategorized?: boolean;
    noteId?: string;
    unlinkedOnly?: boolean;
  } = { workspaceId: workspaceId.value };

  if (filterNoteId.value) {
    query.noteId = filterNoteId.value;
  }

  if (activeFolderKey.value === 'uncategorized') {
    query.uncategorized = true;
  } else if (activeFolderKey.value === 'unlinked') {
    query.unlinkedOnly = true;
  } else if (activeFolderKey.value.startsWith('folder:')) {
    query.folderId = activeFolderKey.value.replace('folder:', '');
  }

  return query;
};

const loadFiles = async () => {
  const query = buildFileQuery();
  if (!query) {
    files.value = [];
    return;
  }
  loading.value = true;
  try {
    files.value = await listDriveFiles(query);
  } catch (error: any) {
    message.error(error?.message || '加载网盘失败');
  } finally {
    loading.value = false;
  }
};

const loadFolders = async () => {
  if (!workspaceId.value) {
    folders.value = [];
    folderExpandedKeys.value = [];
    return;
  }
  foldersLoading.value = true;
  try {
    folders.value = await listDriveFolders(workspaceId.value);
    folderExpandedKeys.value = collectDriveFolderTreeKeys(buildDriveFolderTree(folders.value));
  } catch (error: any) {
    message.error(error?.message || '加载文件夹失败');
  } finally {
    foldersLoading.value = false;
  }
};

const loadCounts = async () => {
  if (!workspaceId.value) {
    counts.value = { all: 0, uncategorized: 0, unlinked: 0 };
    return;
  }
  try {
    const [all, uncategorized, unlinked] = await Promise.all([
      listDriveFiles({ workspaceId: workspaceId.value }),
      listDriveFiles({ workspaceId: workspaceId.value, uncategorized: true }),
      listDriveFiles({ workspaceId: workspaceId.value, unlinkedOnly: true }),
    ]);
    counts.value = {
      all: all.length,
      uncategorized: uncategorized.length,
      unlinked: unlinked.length,
    };
  } catch {
    counts.value = { all: 0, uncategorized: 0, unlinked: 0 };
  }
};

const loadNoteOptions = async () => {
  if (!workspaceId.value) {
    noteOptions.value = [];
    return;
  }
  try {
    const page = await listNotes({ workspaceId: workspaceId.value, size: 100, page: 1 });
    noteOptions.value = page.records.map((note) => ({
      label: note.title || '未命名文档',
      value: note.id,
    }));
  } catch {
    noteOptions.value = [];
  }
};

const refreshAll = async () => {
  await Promise.all([loadFiles(), loadFolders(), loadCounts(), loadNoteOptions()]);
};

const onWorkspaceChange = async () => {
  activeFolderKey.value = 'all';
  filterNoteId.value = undefined;
  clearSelection();
  await refreshAll();
};

const onFolderMenuClick = async ({ key }: { key: string }) => {
  activeFolderKey.value = key;
  await loadFiles();
};

const onFolderTreeSelect = async (keys: (string | number)[]) => {
  const key = String(keys[0] ?? '');
  if (!key.startsWith('folder:')) return;
  activeFolderKey.value = key;
  await loadFiles();
};

const onFolderTreeExpand = (keys: (string | number)[]) => {
  folderExpandedKeys.value = keys.map(String);
};

const currentUploadFolderId = () => {
  if (activeFolderKey.value.startsWith('folder:')) {
    return activeFolderKey.value.replace('folder:', '');
  }
  return undefined;
};

const handleUpload = async (file: File) => {
  if (!workspaceId.value) {
    message.warning('请先选择知识库');
    return false;
  }
  uploading.value = true;
  try {
    await uploadDriveFile(workspaceId.value, file, currentUploadFolderId());
    message.success('上传成功');
    await refreshAll();
  } catch (error: any) {
    message.error(error?.message || '上传失败');
  } finally {
    uploading.value = false;
  }
  return false;
};

const openLinkModal = (file: DriveFile) => {
  linkTargetId.value = file.id;
  linkModalOpen.value = true;
};

const openShareModal = (file: DriveFile) => {
  shareTargetId.value = file.id;
  shareModalOpen.value = true;
};

const onFileAction = (file: DriveFile, event: { key: string }) => {
  const { key } = event;
  if (key === 'share') {
    openShareModal(file);
    return;
  }
  if (key === 'link') {
    openLinkModal(file);
    return;
  }
  if (key === 'move') {
    moveTargetFile.value = file;
    moveModalOpen.value = true;
    return;
  }
  if (key === 'delete') {
    handleDelete(file);
    return;
  }
};

const openNote = (noteId: string) => {
  router.push({
    path: `/notes/${noteId}`,
    query: {
      workspace: workspaceId.value,
      from: 'drive',
    },
  });
};

const handleDelete = (file: DriveFile) => {
  Modal.confirm({
    title: '删除文件？',
    content: file.fileName,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await deleteAttachment(file.id);
      message.success('已删除');
      selectedFileIds.value = selectedFileIds.value.filter((id) => id !== file.id);
      await refreshAll();
    },
  });
};

const clearSelection = () => {
  selectedFileIds.value = [];
};

const handleBatchDownload = async () => {
  if (!selectedFileIds.value.length) return;
  batchDownloading.value = true;
  try {
    await batchDownloadDriveFiles(selectedFileIds.value);
    message.success('下载已开始');
  } catch (error: any) {
    message.error(error?.message || '批量下载失败');
  } finally {
    batchDownloading.value = false;
  }
};

const openCreateFolder = (parentId?: string | null) => {
  folderModalMode.value = 'create';
  folderNameInput.value = '';
  editingFolderId.value = undefined;
  createFolderParentId.value = parentId ?? null;
  folderModalOpen.value = true;
};

const handleCreateMenu = ({ key }: { key: string }) => {
  if (key === 'folder') {
    openCreateFolder(null);
    return;
  }
  if (key === 'subfolder' && selectedFolder.value) {
    openCreateFolder(selectedFolder.value.id);
  }
};

const getContextMenuItems = (node: DriveTreeContextNode): ContextMenuItem[] => {
  if (node.kind === 'root') {
    return [{ key: 'new-folder', label: '新建文件夹' }];
  }
  return [
    { key: 'new-subfolder', label: '新建子文件夹' },
    { key: 'rename-folder', label: '重命名文件夹' },
    { key: 'divider-1', divider: true },
    { key: 'delete-folder', label: '删除文件夹', danger: true },
  ];
};

const activeContextMenuItems = computed(() => {
  if (!contextMenu.node) return [];
  return getContextMenuItems(contextMenu.node);
});

const closeContextMenu = () => {
  contextMenu.open = false;
};

const openContextMenu = (event: MouseEvent, node: DriveTreeContextNode) => {
  closeContextMenu();
  contextMenu.x = event.clientX;
  contextMenu.y = event.clientY;
  contextMenu.node = node;
  contextMenu.open = true;
};

const onTreeBodyContextMenu = (event: MouseEvent) => {
  const target = event.target as HTMLElement;
  if (target.closest('.ant-tree-node-content-wrapper')) return;
  event.preventDefault();
  if (!workspaceId.value) return;
  openContextMenu(event, { kind: 'root' });
};

const onFolderTreeRightClick = ({ event, node }: { event: MouseEvent; node: { key: string | number } }) => {
  event.preventDefault();
  event.stopPropagation();
  const key = String(node.key);
  if (!key.startsWith('folder:')) return;
  const folderId = key.replace('folder:', '');
  const folder = folders.value.find((item) => item.id === folderId);
  if (!folder) return;
  openContextMenu(event, { kind: 'folder', folder });
};

const onContextMenuSelect = async ({ key }: { key: string }) => {
  closeContextMenu();
  const node = contextMenu.node;
  if (!node) return;
  if (key === 'new-folder') {
    openCreateFolder(null);
    return;
  }
  if (node.kind !== 'folder') return;
  if (key === 'new-subfolder') {
    openCreateFolder(node.folder.id);
    return;
  }
  if (key === 'rename-folder') {
    openRenameFolder(node.folder);
    return;
  }
  if (key === 'delete-folder') {
    handleDeleteFolder(node.folder);
  }
};

const onDocumentPointerDown = (event: MouseEvent) => {
  if (!contextMenu.open) return;
  const target = event.target as HTMLElement;
  if (target.closest('.tree-context-menu') || target.closest('.ant-dropdown-menu')) return;
  closeContextMenu();
};

const openRenameFolder = (folder: DriveFolder) => {
  folderModalMode.value = 'rename';
  folderNameInput.value = folder.name;
  editingFolderId.value = folder.id;
  folderModalOpen.value = true;
};

const handleDeleteFolder = (folder: DriveFolder) => {
  Modal.confirm({
    title: '删除文件夹？',
    content: `「${folder.name}」内的文件将移至未分类`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await deleteDriveFolder(folder.id);
      if (activeFolderKey.value === `folder:${folder.id}`) {
        activeFolderKey.value = 'all';
      }
      message.success('文件夹已删除');
      await refreshAll();
    },
  });
};

const handleFolderModalOk = async () => {
  const name = folderNameInput.value.trim();
  if (!name) {
    message.warning('请输入文件夹名称');
    return;
  }
  if (!workspaceId.value) return;
  folderSaving.value = true;
  try {
    if (folderModalMode.value === 'create') {
      await createDriveFolder(workspaceId.value, name, createFolderParentId.value);
      message.success('文件夹已创建');
    } else if (editingFolderId.value) {
      await updateDriveFolder(editingFolderId.value, name);
      message.success('文件夹已重命名');
    }
    folderModalOpen.value = false;
    await refreshAll();
  } catch (error: any) {
    message.error(error?.message || '保存失败');
  } finally {
    folderSaving.value = false;
  }
};

const syncWorkspaceFromRoute = async () => {
  await workspaceStore.ensureLoaded();
  const fromRoute = typeof route.query.workspace === 'string' ? route.query.workspace : '';
  if (fromRoute && workspaceStore.items.some((item) => item.id === fromRoute)) {
    workspaceId.value = fromRoute;
  } else if (!workspaceId.value && workspaceStore.items.length) {
    workspaceId.value = workspaceStore.items[0].id;
  }
};

const refreshDriveForCurrentWorkspace = async () => {
  if (!workspaceId.value) return;
  await refreshAll();
};

watch(
  () => route.query.workspace,
  async (next) => {
    if (typeof next !== 'string' || !next) return;
    if (next === workspaceId.value) return;
    if (!workspaceStore.items.some((item) => item.id === next)) {
      await workspaceStore.ensureLoaded();
    }
    if (workspaceStore.items.some((item) => item.id === next)) {
      workspaceId.value = next;
      await refreshDriveForCurrentWorkspace();
    }
  },
);

onMounted(async () => {
  document.addEventListener('pointerdown', onDocumentPointerDown);
  await syncWorkspaceFromRoute();
  await refreshDriveForCurrentWorkspace();
});

onActivated(async () => {
  await syncWorkspaceFromRoute();
  await refreshDriveForCurrentWorkspace();
});

onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown);
});
</script>

<style scoped>
.drive-hero {
  border-radius: var(--noto-radius-lg);
}

.hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.hero-desc {
  margin: 8px 0 0;
  color: var(--noto-text-muted);
  font-size: 14px;
}

.drive-filters {
  margin-top: 4px;
}

.drive-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.folder-card :deep(.ant-card-body) {
  padding: 12px;
}

.tree-toolbar {
  margin-bottom: 8px;
}

.tree-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tree-body {
  min-height: 120px;
}

.context-menu-anchor {
  position: fixed;
  width: 1px;
  height: 1px;
  pointer-events: none;
}

.folder-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--noto-text);
}

.folder-menu-fixed {
  margin-bottom: 4px;
}

.folder-tree {
  margin-top: 4px;
}

.folder-tree :deep(.ant-tree-node-content-wrapper) {
  border-radius: 8px;
}

.folder-tree :deep(.ant-tree-title) {
  font-size: 13px;
}

.folder-empty {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--noto-text-muted);
}

.folder-modal-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--noto-text-muted);
}

.folder-menu {
  border-inline-end: none !important;
}

.folder-menu :deep(.ant-menu-item) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0;
  width: 100%;
  border-radius: 8px;
}

.folder-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-count {
  font-size: 11px;
  color: var(--noto-text-muted);
  font-family: var(--noto-font-mono);
}

.drive-table-card :deep(.ant-card-body) {
  padding-top: 8px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 0 4px;
}

.selection-hint {
  font-size: 13px;
  color: var(--noto-text-muted);
}

.file-link {
  color: var(--noto-accent-deep, #0891b2);
  font-weight: 500;
}

.file-row-action {
  color: var(--noto-text-muted);
}

.file-row-action:hover {
  color: var(--noto-accent-deep, #0891b2);
  background: var(--noto-pastel-blue, rgba(8, 145, 178, 0.08));
}

.note-tag {
  cursor: pointer;
  margin: 0;
}

.muted-text {
  color: var(--noto-text-muted);
  font-size: 13px;
}

@media (max-width: 960px) {
  .drive-layout {
    grid-template-columns: 1fr;
  }
}
</style>
