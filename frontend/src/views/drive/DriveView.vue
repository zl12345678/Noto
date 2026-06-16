<template>
  <div class="drive-page noto-page">
    <a-card class="drive-hero" :bordered="false">
      <div class="hero-row">
        <div>
          <p class="noto-page-eyebrow">知识库文件</p>
          <h2 class="noto-page-title">网盘</h2>
          <p class="hero-desc">像资源管理器一样浏览文件夹，按文档筛选关联，并支持插入到正文</p>
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
          placeholder="搜索文件名或文件夹"
          allow-clear
          style="min-width: 220px"
        />
      </a-space>
    </a-card>

    <a-card
      class="drive-explorer-card noto-surface-card"
      :bordered="false"
      :loading="loading || foldersLoading"
      @contextmenu="onExplorerBodyContextMenu"
    >
      <div class="explorer-toolbar">
        <div class="explorer-nav">
          <a-button
            type="text"
            class="explorer-back"
            :disabled="!canGoBack"
            aria-label="返回上一级"
            @click="goBack"
          >
            <ArrowLeftOutlined />
          </a-button>
          <a-breadcrumb class="explorer-breadcrumb">
            <a-breadcrumb-item
              v-for="item in breadcrumbItems"
              :key="item.key"
            >
              <a v-if="item.clickable" href="#" @click.prevent="navigateTo(item.key)">{{ item.label }}</a>
              <span v-else>{{ item.label }}</span>
            </a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <div class="explorer-toolbar-actions">
          <a-radio-group
            v-model:value="fileViewMode"
            size="small"
            button-style="solid"
            class="view-toggle"
          >
            <a-radio-button value="grid" aria-label="网格视图">
              <AppstoreOutlined />
            </a-radio-button>
            <a-radio-button value="list" aria-label="列表视图">
              <UnorderedListOutlined />
            </a-radio-button>
          </a-radio-group>
          <a-dropdown :disabled="!workspaceId">
            <a-button size="small">新建文件夹</a-button>
            <template #overlay>
              <a-menu @click="handleCreateMenu">
                <a-menu-item key="folder">新建文件夹</a-menu-item>
                <a-menu-item key="subfolder" :disabled="!currentFolder">新建子文件夹</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </div>

      <div v-if="hasExplorerItems" class="table-toolbar">
          <a-space wrap>
            <span v-if="selectedKeys.length" class="selection-hint">
              已选 {{ selectedKeys.length }} 项
            </span>
            <a-button
              :disabled="!selectedKeys.length"
              :loading="batchDownloading"
              @click="handleBatchDownload"
            >
              批量下载
            </a-button>
            <a-button :disabled="!selectedKeys.length" @click="openBatchShareModal">
              批量分享
            </a-button>
            <a-button type="link" :disabled="!selectedKeys.length" @click="clearSelection">
              取消选择
            </a-button>
          </a-space>
        </div>

        <div v-if="hasExplorerItems && fileViewMode === 'grid'" class="explorer-items-grid">
          <a-checkbox-group v-model:value="selectedKeys" class="explorer-items-group">
            <div
              v-for="item in explorerItems"
              :key="item.key"
              class="explorer-item"
              :class="{
                'is-selected': selectedKeys.includes(item.key),
                'explorer-item--folder': item.kind === 'folder',
                'explorer-item--file': item.kind === 'file',
              }"
              @click="handleGridItemClick(item, $event)"
              @contextmenu="(event) => onExplorerItemContextMenu(event, item)"
            >
              <a-checkbox :value="item.key" class="item-select-check" />
              <a-dropdown
                v-if="item.kind === 'file' || (item.kind === 'folder' && item.folderId)"
                :trigger="['click']"
                @click.stop
              >
                <a-button type="text" size="small" class="item-grid-action" aria-label="操作">
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu v-if="item.kind === 'file'" @click="handleGridFileAction(item.file)">
                    <a-menu-item key="share">分享</a-menu-item>
                    <a-menu-item key="link">关联文档</a-menu-item>
                    <a-menu-item key="move">移动至…</a-menu-item>
                    <a-menu-divider />
                    <a-menu-item key="delete" danger>删除</a-menu-item>
                  </a-menu>
                  <a-menu v-else @click="handleGridFolderAction(item)">
                    <a-menu-item key="rename">重命名</a-menu-item>
                    <a-menu-divider />
                    <a-menu-item key="delete" danger>删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
              <template v-if="item.kind === 'folder'">
                <FolderOutlined class="explorer-item-icon explorer-item-icon--folder" />
                <span class="explorer-item-name" :title="item.name">{{ item.name }}</span>
                <span class="explorer-item-meta">{{ item.count }} 项</span>
              </template>
              <template v-else>
                <component :is="getFileIcon(item.file)" class="explorer-item-icon" />
                <a
                  :href="item.file.fileUrl"
                  target="_blank"
                  rel="noopener"
                  class="explorer-item-name"
                  :title="item.name"
                  @click.stop
                >
                  {{ item.name }}
                </a>
                <span class="explorer-item-meta">{{ formatSize(item.file.fileSize) }}</span>
                <span v-if="item.file.linkedNotes?.length" class="explorer-item-linked">
                  {{ item.file.linkedNotes.length }} 篇文档
                </span>
              </template>
            </div>
          </a-checkbox-group>
        </div>

        <a-table
          v-if="hasExplorerItems && fileViewMode === 'list'"
          :data-source="explorerItems"
          :columns="columns"
          :pagination="false"
          :row-selection="rowSelection"
          :custom-row="customTableRow"
          :scroll="isMobile ? undefined : { x: 720 }"
          row-key="key"
          size="middle"
          class="drive-file-table"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="drive-name-cell">
                <template v-if="record.kind === 'folder'">
                  <FolderOutlined class="list-item-icon list-item-icon--folder" />
                  <span class="folder-link drive-name-text" :title="record.name">{{ record.name }}</span>
                </template>
                <template v-else>
                  <component :is="getFileIcon(record.file)" class="list-item-icon" />
                  <a
                    :href="record.file.fileUrl"
                    target="_blank"
                    rel="noopener"
                    class="file-link drive-name-text"
                    :title="record.name"
                  >
                    {{ record.name }}
                  </a>
                </template>
              </div>
            </template>
            <template v-else-if="column.key === 'itemType'">
              {{ record.kind === 'folder' ? '文件夹' : '文件' }}
            </template>
            <template v-else-if="column.key === 'size'">
              <template v-if="record.kind === 'folder'">{{ record.count }} 项</template>
              <template v-else>{{ formatSize(record.file.fileSize) }}</template>
            </template>
            <template v-else-if="column.key === 'linkedNotes'">
              <template v-if="record.kind === 'file'">
                <a-space v-if="record.file.linkedNotes?.length" wrap :size="4">
                  <a-tag
                    v-for="note in record.file.linkedNotes"
                    :key="note.id"
                    class="note-tag"
                    @click="openNote(note.id)"
                  >
                    {{ note.title || '未命名文档' }}
                  </a-tag>
                </a-space>
                <span v-else class="muted-text">未关联</span>
              </template>
              <span v-else class="muted-text">—</span>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ record.kind === 'file' ? formatTime(record.file.createdAt) : '—' }}
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-dropdown v-if="record.kind === 'file'" :trigger="['click']">
                <a-button type="text" size="small" class="file-row-action" aria-label="文件操作">
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu @click="handleGridFileAction(record.file)">
                    <a-menu-item key="share">分享</a-menu-item>
                    <a-menu-item key="link">关联文档</a-menu-item>
                    <a-menu-item key="move">移动至…</a-menu-item>
                    <a-menu-divider />
                    <a-menu-item key="delete" danger>删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
              <a-dropdown v-else-if="record.folderId" :trigger="['click']">
                <a-button type="text" size="small" class="file-row-action" aria-label="文件夹操作">
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu @click="handleGridFolderAction(record)">
                    <a-menu-item key="rename">重命名</a-menu-item>
                    <a-menu-divider />
                    <a-menu-item key="delete" danger>删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </template>
          </template>
        </a-table>

        <EmptyState
          v-else-if="workspaceId && !loading && !foldersLoading && !hasExplorerItems"
          :title="emptyStateTitle"
          :description="emptyStateDescription"
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

    <BatchShareModal v-model:open="batchShareModalOpen" :file-ids="batchShareFileIds" />

    <a-dropdown
      v-model:open="contextMenu.open"
      :trigger="[]"
      overlay-class-name="explorer-context-menu"
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
import {
  AppstoreOutlined,
  ArrowLeftOutlined,
  FileExcelOutlined,
  FileImageOutlined,
  FileOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  FileZipOutlined,
  FolderOutlined,
  MoreOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons-vue';
import EmptyState from '../../components/common/EmptyState.vue';
import DriveLinkNoteModal from '../../components/drive/DriveLinkNoteModal.vue';
import DriveMoveFolderModal from '../../components/drive/DriveMoveFolderModal.vue';
import BatchShareModal from '../../components/share/BatchShareModal.vue';
import ShareLinkModal from '../../components/share/ShareLinkModal.vue';
import { deleteAttachment } from '../../api/attachments';
import { listNotes } from '../../api/notes';
import {
  batchDownloadDriveSelection,
  createDriveFolder,
  deleteDriveFolder,
  listDriveFiles,
  listDriveFolders,
  updateDriveFolder,
  uploadDriveFile,
  type DriveFile,
  type DriveFolder,
  type DriveBatchDownloadPayload,
} from '../../api/drive';
import { useWorkspaceStore } from '../../store/workspace';
import {
  getDriveFolderAncestors,
  getDriveFolderDescendantIds,
  getDriveFolderParentId,
} from '../../utils/driveFolderTree';
import { normalizeDriveFile, resolveDriveFileDisplayName } from '../../utils/driveFileName';
import { useBreakpoint } from '../../composables/useBreakpoint';

type DriveLocationKey = 'root' | 'uncategorized' | 'unlinked' | `folder:${string}`;
type FileViewMode = 'list' | 'grid';

const FILE_VIEW_STORAGE_KEY = 'noto-drive-file-view';

function readStoredFileViewMode(): FileViewMode {
  const raw = localStorage.getItem(FILE_VIEW_STORAGE_KEY);
  return raw === 'grid' ? 'grid' : 'list';
}

type ExplorerFolderItem = {
  key: DriveLocationKey;
  name: string;
  count: number;
  folderId?: string;
  virtual?: boolean;
};

type ExplorerItem =
  | {
      kind: 'folder';
      key: string;
      name: string;
      count: number;
      locationKey: DriveLocationKey;
      folderId?: string;
      virtual?: boolean;
    }
  | {
      kind: 'file';
      key: string;
      name: string;
      file: DriveFile;
    };

const route = useRoute();
const router = useRouter();
const workspaceStore = useWorkspaceStore();
const { isMobile } = useBreakpoint();

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
const fileViewMode = ref<FileViewMode>(readStoredFileViewMode());
const selectedKeys = ref<string[]>([]);
const batchShareFileIds = ref<string[]>([]);
const linkModalOpen = ref(false);
const linkTargetId = ref<string>();
const shareModalOpen = ref(false);
const shareTargetId = ref<string>();
const batchShareModalOpen = ref(false);
const moveModalOpen = ref(false);
const moveTargetFile = ref<DriveFile | null>(null);
const currentLocationKey = ref<DriveLocationKey>('root');
const counts = ref({ all: 0, uncategorized: 0, unlinked: 0 });

const folderModalOpen = ref(false);
const folderModalMode = ref<'create' | 'rename'>('create');
const folderNameInput = ref('');
const folderSaving = ref(false);
const editingFolderId = ref<string>();
const createFolderParentId = ref<string | null>(null);

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

const currentFolder = computed(() => {
  if (!currentLocationKey.value.startsWith('folder:')) return null;
  const id = currentLocationKey.value.replace('folder:', '');
  return folders.value.find((item) => item.id === id) ?? null;
});

const currentParentId = computed(() => {
  if (currentLocationKey.value.startsWith('folder:')) {
    return currentLocationKey.value.replace('folder:', '');
  }
  return null;
});

const breadcrumbItems = computed(() => {
  const items: Array<{ key: DriveLocationKey; label: string; clickable: boolean }> = [
    { key: 'root', label: '网盘', clickable: currentLocationKey.value !== 'root' },
  ];

  if (currentLocationKey.value === 'uncategorized') {
    items.push({ key: 'uncategorized', label: '未分类', clickable: false });
    return items;
  }
  if (currentLocationKey.value === 'unlinked') {
    items.push({ key: 'unlinked', label: '未关联文档', clickable: false });
    return items;
  }
  if (currentLocationKey.value.startsWith('folder:')) {
    const folderId = currentLocationKey.value.replace('folder:', '');
    const ancestors = getDriveFolderAncestors(folderId, folders.value);
    ancestors.forEach((folder, index) => {
      const isLast = index === ancestors.length - 1;
      items.push({
        key: `folder:${folder.id}`,
        label: folder.name,
        clickable: !isLast,
      });
    });
  }
  return items;
});

const canGoBack = computed(() => currentLocationKey.value !== 'root');

const explorerFolders = computed<ExplorerFolderItem[]>(() => {
  const q = keyword.value.trim();
  if (q) {
    return folders.value
      .slice()
      .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
      .map((folder) => ({
        key: `folder:${folder.id}` as DriveLocationKey,
        name: folder.name,
        count: folder.fileCount ?? 0,
        folderId: folder.id,
      }));
  }

  if (currentLocationKey.value === 'uncategorized' || currentLocationKey.value === 'unlinked') {
    return [];
  }

  const parentId =
    currentLocationKey.value === 'root'
      ? null
      : currentLocationKey.value.replace('folder:', '');

  const childFolders = folders.value
    .filter((folder) => (folder.parentId ?? null) === parentId)
    .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
    .map((folder) => ({
      key: `folder:${folder.id}` as DriveLocationKey,
      name: folder.name,
      count: folder.fileCount ?? 0,
      folderId: folder.id,
    }));

  if (currentLocationKey.value !== 'root') {
    return childFolders;
  }

  return [
    ...childFolders,
    {
      key: 'uncategorized' as DriveLocationKey,
      name: '未分类',
      count: counts.value.uncategorized,
      virtual: true,
    },
    {
      key: 'unlinked' as DriveLocationKey,
      name: '未关联文档',
      count: counts.value.unlinked,
      virtual: true,
    },
  ];
});

const emptyStateTitle = computed(() => {
  if (keyword.value.trim()) return '没有匹配的项目';
  if (currentLocationKey.value === 'root') return '此目录为空';
  if (currentLocationKey.value === 'uncategorized') return '暂无未分类文件';
  if (currentLocationKey.value === 'unlinked') return '暂无未关联文档的文件';
  return '此文件夹为空';
});

const emptyStateDescription = computed(() => {
  if (keyword.value.trim()) {
    return '试试其他关键词，或进入文件夹浏览';
  }
  if (currentLocationKey.value === 'root') {
    return '新建文件夹或上传文件；也可在文档附件侧栏从网盘关联';
  }
  return '上传文件到此文件夹，或返回上级目录浏览';
});

const createFolderParentName = computed(() => {
  if (!createFolderParentId.value) return '';
  return folders.value.find((item) => item.id === createFolderParentId.value)?.name ?? '';
});

const workspaceOptions = computed(() =>
  workspaceStore.items.map((item) => ({ label: item.name, value: item.id })),
);

const filteredFiles = computed(() => files.value);

const folderSelectionKey = (folder: ExplorerFolderItem) =>
  folder.virtual ? `location:${folder.key}` : `folder:${folder.folderId}`;

const explorerItems = computed<ExplorerItem[]>(() => {
  const folderItems: ExplorerItem[] = explorerFolders.value.map((folder) => ({
      kind: 'folder' as const,
      key: folderSelectionKey(folder),
      name: folder.name,
      count: folder.count,
      locationKey: folder.key,
      folderId: folder.folderId,
      virtual: folder.virtual,
    }));

  const fileItems: ExplorerItem[] = filteredFiles.value.map((file) => ({
    kind: 'file' as const,
    key: `file:${file.id}`,
    name: resolveDriveFileDisplayName(file),
    file,
  }));

  return [...folderItems, ...fileItems];
});

const hasExplorerItems = computed(() => explorerItems.value.length > 0);

const rowSelection = computed(() => ({
  type: 'checkbox' as const,
  selectedRowKeys: selectedKeys.value,
  onChange: (keys: (string | number)[]) => {
    selectedKeys.value = keys.map(String);
  },
}));

const customTableRow = (record: ExplorerItem) => ({
  onClick: (event: MouseEvent) => {
    const target = event.target as HTMLElement;
    if (
      target.closest('.ant-checkbox-wrapper')
      || target.closest('.ant-table-selection-column')
      || target.closest('.file-row-action')
      || target.closest('.ant-dropdown')
      || target.closest('a')
    ) {
      return;
    }
    if (record.kind === 'folder') {
      void navigateTo(record.locationKey);
    }
  },
  style: record.kind === 'folder' ? { cursor: 'pointer' } : {},
});

const columns = computed(() => {
  if (isMobile.value) {
    return [
      { title: '名称', key: 'name', ellipsis: true },
      { title: '大小', key: 'size', width: 72 },
      { title: '操作', key: 'actions', width: 48, align: 'center' as const },
    ];
  }
  return [
    { title: '名称', key: 'name', ellipsis: true, minWidth: 160 },
    { title: '类型', key: 'itemType', width: 90 },
    { title: '大小', key: 'size', width: 90 },
    { title: '关联文档', key: 'linkedNotes' },
    { title: '上传时间', key: 'createdAt', width: 140 },
    { title: '操作', key: 'actions', width: 72, align: 'center' as const },
  ];
});

const filterNoteOption = (input: string, option?: { label?: string; value?: string }) =>
  (option?.label ?? '').toLowerCase().includes(input.toLowerCase());

const formatSize = (bytes: number) => {
  if (!bytes || bytes < 1024) return `${bytes || 0} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const formatTime = (value?: string) => (value ? dayjs(value).format('MM-DD HH:mm') : '—');

const getFileExtension = (file: DriveFile) => {
  const name = file.fileName.toLowerCase();
  const dot = name.lastIndexOf('.');
  return dot >= 0 ? name.slice(dot + 1) : '';
};

const getFileIcon = (file: DriveFile) => {
  const ext = getFileExtension(file);
  const type = (file.fileType || '').toLowerCase();
  if (type.startsWith('image/') || ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) {
    return FileImageOutlined;
  }
  if (type.includes('pdf') || ext === 'pdf') return FilePdfOutlined;
  if (['doc', 'docx'].includes(ext) || type.includes('word')) return FileWordOutlined;
  if (['xls', 'xlsx', 'csv'].includes(ext) || type.includes('sheet') || type.includes('excel')) {
    return FileExcelOutlined;
  }
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return FileZipOutlined;
  return FileOutlined;
};

const toggleItemSelection = (key: string, checked: boolean) => {
  if (checked) {
    if (!selectedKeys.value.includes(key)) {
      selectedKeys.value = [...selectedKeys.value, key];
    }
    return;
  }
  selectedKeys.value = selectedKeys.value.filter((item) => item !== key);
};

const isInteractiveGridTarget = (target: HTMLElement) =>
  Boolean(
    target.closest('.ant-checkbox-wrapper')
    || target.closest('.ant-checkbox')
    || target.closest('.item-grid-action')
    || target.closest('.ant-dropdown-trigger')
    || target.closest('a'),
  );

const handleGridItemClick = (item: ExplorerItem, event: MouseEvent) => {
  if (isInteractiveGridTarget(event.target as HTMLElement)) return;
  if (item.kind === 'folder') {
    void navigateTo(item.locationKey);
  }
};

const handleGridFileAction = (file: DriveFile) => (event: { key: string }) => {
  onFileAction(file, event);
};

const handleGridFolderAction = (item: ExplorerItem & { kind: 'folder' }) => (event: { key: string }) => {
  if (!item.folderId) return;
  const folder = folders.value.find((entry) => entry.id === item.folderId);
  if (!folder) return;
  if (event.key === 'rename') {
    openRenameFolder(folder);
    return;
  }
  if (event.key === 'delete') {
    handleDeleteFolder(folder);
  }
};

const resolveBatchDownloadPayload = (): DriveBatchDownloadPayload | null => {
  if (!workspaceId.value || !selectedKeys.value.length) return null;

  const ids: string[] = [];
  const folderIds: string[] = [];
  let uncategorized = false;
  let unlinkedOnly = false;

  for (const key of selectedKeys.value) {
    if (key.startsWith('file:')) {
      ids.push(key.replace('file:', ''));
      continue;
    }
    if (key.startsWith('folder:')) {
      folderIds.push(key.replace('folder:', ''));
      continue;
    }
    if (key === 'location:uncategorized') {
      uncategorized = true;
      continue;
    }
    if (key === 'location:unlinked') {
      unlinkedOnly = true;
    }
  }

  return {
    workspaceId: workspaceId.value,
    ids: ids.length ? ids : undefined,
    folderIds: folderIds.length ? folderIds : undefined,
    uncategorized: uncategorized || undefined,
    unlinkedOnly: unlinkedOnly || undefined,
  };
};

const resolveSelectedFileIds = async (): Promise<string[]> => {
  const payload = resolveBatchDownloadPayload();
  if (!payload) return [];
  const fileIds = payload.ids ?? [];
  if (!payload.folderIds?.length && !payload.uncategorized && !payload.unlinkedOnly) {
    return fileIds;
  }
  // 文件夹/虚拟目录仍需要解析文件 id 供批量分享
  if (payload.folderIds?.length) {
    for (const folderId of payload.folderIds) {
      const scopedIds = [folderId, ...getDriveFolderDescendantIds(folderId, folders.value)];
      for (const targetFolderId of scopedIds) {
        const folderFiles = await listDriveFiles({
          workspaceId: workspaceId.value!,
          folderId: targetFolderId,
        });
        folderFiles.forEach((file) => fileIds.push(file.id));
      }
    }
  }
  if (payload.uncategorized) {
    const uncategorized = await listDriveFiles({
      workspaceId: workspaceId.value!,
      uncategorized: true,
    });
    uncategorized.forEach((file) => fileIds.push(file.id));
  }
  if (payload.unlinkedOnly) {
    const unlinked = await listDriveFiles({
      workspaceId: workspaceId.value!,
      unlinkedOnly: true,
    });
    unlinked.forEach((file) => fileIds.push(file.id));
  }
  return [...new Set(fileIds)];
};

const buildFileQuery = () => {
  if (!workspaceId.value) return null;
  const query: {
    workspaceId: string;
    folderId?: string;
    uncategorized?: boolean;
    noteId?: string;
    unlinkedOnly?: boolean;
    keyword?: string;
  } = { workspaceId: workspaceId.value };

  if (filterNoteId.value) {
    query.noteId = filterNoteId.value;
  }

  const q = keyword.value.trim();
  if (q) {
    query.keyword = q;
    return query;
  }

  if (currentLocationKey.value === 'root') {
    return null;
  }

  if (currentLocationKey.value === 'uncategorized') {
    query.uncategorized = true;
  } else if (currentLocationKey.value === 'unlinked') {
    query.unlinkedOnly = true;
  } else if (currentLocationKey.value.startsWith('folder:')) {
    query.folderId = currentLocationKey.value.replace('folder:', '');
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
    const list = await listDriveFiles(query);
    files.value = list.map((item) => normalizeDriveFile(item));
  } catch (error: any) {
    message.error(error?.message || '加载网盘失败');
  } finally {
    loading.value = false;
  }
};

const loadFolders = async () => {
  if (!workspaceId.value) {
    folders.value = [];
    return;
  }
  foldersLoading.value = true;
  try {
    const q = keyword.value.trim();
    folders.value = await listDriveFolders(workspaceId.value, q || undefined);
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
  currentLocationKey.value = 'root';
  filterNoteId.value = undefined;
  keyword.value = '';
  clearSelection();
  await refreshAll();
};

const navigateTo = async (key: DriveLocationKey) => {
  if (key === currentLocationKey.value) return;
  currentLocationKey.value = key;
  clearSelection();
  await loadFiles();
};

const goBack = async () => {
  if (currentLocationKey.value === 'root') return;
  if (currentLocationKey.value === 'uncategorized' || currentLocationKey.value === 'unlinked') {
    await navigateTo('root');
    return;
  }
  if (currentLocationKey.value.startsWith('folder:')) {
    const folderId = currentLocationKey.value.replace('folder:', '');
    const parentId = getDriveFolderParentId(folderId, folders.value);
    await navigateTo(parentId ? `folder:${parentId}` : 'root');
  }
};

const currentUploadFolderId = () => {
  if (currentLocationKey.value.startsWith('folder:')) {
    return currentLocationKey.value.replace('folder:', '');
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
      selectedKeys.value = selectedKeys.value.filter((key) => key !== `file:${file.id}`);
      await refreshAll();
    },
  });
};

const clearSelection = () => {
  selectedKeys.value = [];
};

const handleBatchDownload = async () => {
  const payload = resolveBatchDownloadPayload();
  if (!payload) return;
  batchDownloading.value = true;
  try {
    await batchDownloadDriveSelection(payload);
    message.success('下载已开始');
  } catch (error: any) {
    message.error(error?.message || '批量下载失败');
  } finally {
    batchDownloading.value = false;
  }
};

const openBatchShareModal = async () => {
  if (!selectedKeys.value.length) return;
  try {
    const fileIds = await resolveSelectedFileIds();
    if (!fileIds.length) {
      message.warning('所选项目中没有可分享的文件');
      return;
    }
    batchShareFileIds.value = fileIds;
    batchShareModalOpen.value = true;
  } catch (error: any) {
    message.error(error?.message || '解析所选文件失败');
  }
};

const openCreateFolder = (parentId?: string | null) => {
  folderModalMode.value = 'create';
  folderNameInput.value = '';
  editingFolderId.value = undefined;
  createFolderParentId.value = parentId ?? currentParentId.value;
  folderModalOpen.value = true;
};

const handleCreateMenu = ({ key }: { key: string }) => {
  if (key === 'folder') {
    openCreateFolder(currentParentId.value);
    return;
  }
  if (key === 'subfolder' && currentFolder.value) {
    openCreateFolder(currentFolder.value.id);
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

const onExplorerBodyContextMenu = (event: MouseEvent) => {
  const target = event.target as HTMLElement;
  if (target.closest('.explorer-item') || target.closest('.ant-table-row')) {
    return;
  }
  event.preventDefault();
  if (!workspaceId.value) return;
  if (currentLocationKey.value === 'uncategorized' || currentLocationKey.value === 'unlinked') return;
  openContextMenu(event, { kind: 'root' });
};

const onExplorerItemContextMenu = (event: MouseEvent, item: ExplorerItem) => {
  if (item.kind !== 'folder' || item.virtual || !item.folderId) return;
  event.preventDefault();
  event.stopPropagation();
  const folderRecord = folders.value.find((entry) => entry.id === item.folderId);
  if (!folderRecord) return;
  openContextMenu(event, { kind: 'folder', folder: folderRecord });
};

const onContextMenuSelect = async ({ key }: { key: string }) => {
  closeContextMenu();
  const node = contextMenu.node;
  if (!node) return;
  if (key === 'new-folder') {
    openCreateFolder(currentParentId.value);
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
  if (target.closest('.explorer-context-menu') || target.closest('.ant-dropdown-menu')) return;
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
      if (currentLocationKey.value === `folder:${folder.id}`) {
        currentLocationKey.value = 'root';
      }
      selectedKeys.value = selectedKeys.value.filter((key) => key !== `folder:${folder.id}`);
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

watch(fileViewMode, (mode) => {
  localStorage.setItem(FILE_VIEW_STORAGE_KEY, mode);
});

watch(
  () => keyword.value,
  async () => {
    if (!workspaceId.value) return;
    await Promise.all([loadFiles(), loadFolders()]);
  },
);

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

.drive-explorer-card :deep(.ant-card-body) {
  padding: 12px 16px 16px;
}

.explorer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.explorer-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.view-toggle :deep(.ant-radio-button-wrapper) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 28px;
  padding: 0;
  line-height: 1;
}

.view-toggle :deep(.ant-radio-button-wrapper > span:not(.ant-radio-button)) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 0;
  line-height: 0;
}

.view-toggle :deep(.ant-radio-button-wrapper .anticon) {
  display: block;
  font-size: 14px;
  line-height: 1;
}

.explorer-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.explorer-back {
  flex-shrink: 0;
  color: var(--noto-text-muted);
}

.explorer-back:not(:disabled):hover {
  color: var(--noto-accent-deep, #0891b2);
}

.explorer-breadcrumb {
  min-width: 0;
}

.explorer-breadcrumb :deep(.ant-breadcrumb-link),
.explorer-breadcrumb :deep(.ant-breadcrumb-link a) {
  font-size: 13px;
}

.explorer-items-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(148px, 1fr));
  gap: 8px;
}

.explorer-items-group {
  display: contents;
}

.explorer-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 34px 12px 12px;
  border: 1px solid var(--noto-border-soft, #eef2f7);
  border-radius: 10px;
  background: var(--noto-surface, #fff);
  text-align: left;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.explorer-item--folder {
  cursor: pointer;
}

.explorer-item:hover,
.explorer-item.is-selected {
  border-color: var(--noto-accent, #22d3ee);
  background: var(--noto-pastel-blue, rgba(8, 145, 178, 0.06));
}

.item-select-check {
  position: absolute;
  top: 8px;
  left: 10px;
  z-index: 3;
  margin: 0;
}

.item-select-check :deep(.ant-checkbox) {
  top: 0;
}

.item-grid-action {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 2;
  color: var(--noto-text-muted);
  opacity: 0;
  transition: opacity 0.15s ease;
}

.explorer-item:hover .item-grid-action,
.explorer-item.is-selected .item-grid-action {
  opacity: 1;
}

.item-grid-action:hover {
  color: var(--noto-accent-deep, #0891b2);
  background: var(--noto-pastel-blue, rgba(8, 145, 178, 0.08));
}

.explorer-item-icon {
  font-size: 36px;
  line-height: 1;
  color: var(--noto-accent-deep, #0891b2);
}

.explorer-item-icon--folder {
  font-size: 34px;
}

.explorer-item-name {
  width: 100%;
  font-size: 13px;
  font-weight: 500;
  color: var(--noto-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

a.explorer-item-name:hover {
  color: var(--noto-accent-deep, #0891b2);
}

.explorer-item-meta {
  font-size: 11px;
  color: var(--noto-text-muted);
  font-family: var(--noto-font-mono);
}

.explorer-item-linked {
  font-size: 11px;
  color: var(--noto-accent-deep, #0891b2);
}

.list-item-icon {
  margin-right: 8px;
  color: var(--noto-accent-deep, #0891b2);
}

.list-item-icon--folder {
  font-size: 14px;
}

.drive-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  max-width: 100%;
}

.drive-name-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-file-table :deep(.ant-table-cell) {
  color: var(--noto-text);
}

.folder-link {
  color: var(--noto-text);
  font-weight: 500;
}

.context-menu-anchor {
  position: fixed;
  width: 1px;
  height: 1px;
  pointer-events: none;
}

.folder-modal-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--noto-text-muted);
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

@media (max-width: 640px) {
  .explorer-items-grid {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  }

  .explorer-toolbar {
    flex-wrap: wrap;
  }

  .item-grid-action {
    opacity: 1;
  }
}
</style>
