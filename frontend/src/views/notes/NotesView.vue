<template>
  <div class="notes-workspace">
    <aside
      v-show="!treeCollapsed"
      class="tree-pane"
      :style="treePaneStyle"
    >
      <div class="tree-toolbar">
        <div class="tree-head">
          <h2 class="tree-title">{{ currentWorkspaceName }}</h2>
          <a-space :size="2" align="center">
            <a-tooltip title="拖拽时：横线 = 插入同级，背景加深 = 放入子级">
              <a-button type="text" size="small" class="tree-icon-btn">
                <QuestionCircleOutlined />
              </a-button>
            </a-tooltip>
            <a-dropdown>
              <a-button type="primary" size="small">新建</a-button>
              <template #overlay>
                <a-menu @click="handleCreateMenu">
                  <a-menu-item key="folder">新建分组</a-menu-item>
                  <a-menu-item key="doc">新建文档</a-menu-item>
                  <a-menu-item key="import">导入剪藏</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </div>
        <a-input-search
          v-model:value="searchKeyword"
          placeholder="搜索文档"
          allow-clear
          class="tree-search"
          @search="reloadNotes"
        />
        <a-segmented
          v-model:value="activeFilter"
          block
          size="small"
          class="tree-filter"
          :options="noteFilterOptions"
          @change="onNoteFilterChange"
        />
        <p v-if="activeFilter !== 'all'" class="tree-create-hint tree-drag-hint">
          切换为「全部」后可拖拽排序
        </p>
        <p v-else-if="createAnchor.kind !== 'top'" class="tree-create-hint">{{ createAnchorLabel }}</p>
      </div>
      <a-spin :spinning="loading">
        <div
          class="tree-body"
          @contextmenu="onTreeBodyContextMenu"
        >
          <NoteDocTree
            v-if="treeData.length > 0"
            v-model="displayTreeData"
            :selected-keys="selectedTreeKeys"
            :expanded-keys="expandedTreeKeys"
            :drag-enabled="activeFilter === 'all'"
            @update:expanded-keys="expandedTreeKeys = $event"
            @select="handleTreeSelect"
            @tree-change="onTreeChange"
            @contextmenu="onTreeRightClick"
          />
          <EmptyState
            v-else
            :title="treeEmptyCopy.title"
            :description="treeEmptyCopy.description"
            :preset="treeEmptyCopy.preset"
          >
            <a-dropdown v-if="activeFilter !== 'archived'">
              <a-button type="primary">+ 新建</a-button>
              <template #overlay>
                <a-menu @click="handleCreateMenu">
                  <a-menu-item key="folder">新建分组</a-menu-item>
                  <a-menu-item key="doc">新建文档</a-menu-item>
                  <a-menu-item key="import">导入剪藏</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </EmptyState>
        </div>
      </a-spin>
    </aside>

    <div class="tree-rail">
      <SidebarResizeHandle
        inline
        :collapsed="treeCollapsed"
        @toggle="sidebarLayout.toggleTree()"
        @resize="sidebarLayout.resizeTree"
      />
    </div>

    <section class="editor-pane">
      <a-spin :spinning="detailLoading" wrapper-class-name="editor-spin">
        <div v-if="note" class="editor-shell">
          <header class="editor-topbar">
            <div class="editor-status">
              <span v-if="saving" class="save-badge">保存中...</span>
              <span v-else-if="isDirty" class="dirty-badge">未保存</span>
              <span v-else-if="autoSavedVisible" class="saved-badge">已自动保存</span>
            </div>
            <div class="editor-actions">
              <a-button
                type="text"
                size="small"
                :class="{ 'ai-toggle--active': aiPanelOpen }"
                @click="openAiPanel"
              >
                <ThunderboltOutlined />
                <span class="action-label">AI</span>
              </a-button>
              <a-button
                type="text"
                size="small"
                class="meta-action-btn"
                :class="{ 'ai-toggle--active': contextDrawerOpen }"
                @click="toggleContextDrawer"
              >
                <LinkOutlined />
                <span class="action-label">上下文</span>
                <a-badge
                  v-if="contextBadgeCount"
                  :count="contextBadgeCount"
                  :overflow-count="99"
                  class="meta-badge"
                />
              </a-button>
              <a-dropdown trigger="click" placement="bottomRight">
                <a-button type="text" size="small" class="meta-action-btn">
                  <span class="action-label">待办</span>
                  <a-badge
                    v-if="linkedTodos.length"
                    :count="linkedTodos.length"
                    :overflow-count="99"
                    class="meta-badge"
                  />
                  <DownOutlined class="action-caret" />
                </a-button>
                <template #overlay>
                  <div class="meta-dropdown" @click.stop>
                    <div v-if="linkedTodos.length" class="meta-dropdown-section">
                      <p class="meta-dropdown-title">本文待办</p>
                      <ul class="linked-todo-list">
                        <li v-for="item in linkedTodos" :key="item.id" class="linked-todo-item">
                          <span class="linked-todo-title">{{ item.title }}</span>
                          <a-space size="small">
                            <a-button type="link" size="small" @click="completeLinkedTodo(item)">完成</a-button>
                            <a-button type="link" size="small" @click="joinLinkedParallel(item)">开始</a-button>
                          </a-space>
                        </li>
                      </ul>
                      <a-button type="link" size="small" block @click="goTodosForNote">查看全部 →</a-button>
                    </div>
                    <a-empty v-else description="暂无关联待办" :image-style="{ height: '48px' }" />
                    <a-divider style="margin: 10px 0" />
                    <a-space direction="vertical" style="width: 100%">
                      <a-button block size="small" @click="goCreateTodo(note.id, note.title, 'action')">
                        新建近期行动
                      </a-button>
                      <a-button block size="small" @click="goCreateTodo(note.id, note.title, 'long_term')">
                        新建长期待办
                      </a-button>
                    </a-space>
                  </div>
                </template>
              </a-dropdown>
              <a-button
                type="text"
                size="small"
                class="meta-action-btn"
                :class="{ 'ai-toggle--active': attachmentDrawerOpen }"
                @click="toggleAttachmentDrawer"
              >
                <PaperClipOutlined />
                <span class="action-label">附件</span>
                <a-badge
                  v-if="attachmentCount"
                  :count="attachmentCount"
                  :overflow-count="99"
                  class="meta-badge"
                />
              </a-button>
              <a-button type="text" size="small" class="meta-action-btn" @click="shareModalOpen = true">
                <ShareAltOutlined />
                <span class="action-label">分享</span>
              </a-button>
              <a-divider type="vertical" class="topbar-divider" />
              <a-button type="text" size="small" @click="syncFromNote" :loading="saving">恢复</a-button>
              <a-button type="primary" size="small" @click="saveDetail" :loading="saving" :disabled="!isDirty">保存</a-button>
            </div>
          </header>

          <div v-if="writeNudgeVisible" class="write-nudge-bar">
            <span class="write-nudge-text">提取待办或生成摘要？</span>
            <a-space size="small">
              <a-button size="small" @click="handleNudgeExtractTodos">提取待办</a-button>
              <a-button size="small" @click="handleNudgeSummarize" :loading="nudgeSummarizing">生成摘要</a-button>
              <a-button type="text" size="small" @click="dismissWriteNudge">关闭</a-button>
            </a-space>
          </div>

          <div
            v-if="(routeSearchKeyword || routeHighlightStart != null) && showRouteSearchAlert"
            class="search-context-bar"
          >
            <span>{{ searchContextText }}</span>
            <a-button type="text" size="small" @click="showRouteSearchAlert = false">关闭</a-button>
          </div>

          <div class="editor-title-row">
            <a-input
              v-model:value="formState.title"
              class="title-field"
              :bordered="false"
              placeholder="无标题文档"
            />
          </div>

          <div class="editor-canvas">
            <MarkdownEditor
              ref="markdownEditorRef"
              v-model="formState.content"
              :note-id="activeNoteId || undefined"
              height="100%"
              :scroll-to-start="contentHighlightRange.start"
              :scroll-to-end="contentHighlightRange.end"
              @save="() => saveDetail()"
            />
            <SelectionFloatingToolbar
              v-if="note && aiEnabled"
              :note-id="note.id"
              :workspace-id="note.workspaceId"
              :title="formState.title"
              :content="formState.content"
              :ai-enabled="aiEnabled"
              @replace="replaceEditorSelection"
              @todo-created="onSelectionTodoCreated"
            />
          </div>
        </div>

        <div v-else-if="directoryContext" class="directory-shell">
          <div class="directory-head">
            <div class="directory-breadcrumb">
              <a-breadcrumb>
                <a-breadcrumb-item>
                  <a @click.prevent="goToNotesRoot">{{ currentWorkspaceName }}</a>
                </a-breadcrumb-item>
                <a-breadcrumb-item v-for="item in directoryBreadcrumb" :key="item.id">
                  <a v-if="item.id !== directoryContext.folderId" @click.prevent="openFolderDirectory(item.id)">
                    {{ item.name }}
                  </a>
                  <span v-else>{{ item.name }}</span>
                </a-breadcrumb-item>
              </a-breadcrumb>
            </div>
            <a-dropdown>
              <a-button type="primary" size="small">+ 新建</a-button>
              <template #overlay>
                <a-menu @click="handleCreateMenu">
                  <a-menu-item key="folder">新建子分组</a-menu-item>
                  <a-menu-item key="doc">新建文档</a-menu-item>
                  <a-menu-item key="import">导入剪藏</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>

          <div v-if="directorySubfolders.length" class="directory-section">
            <h4>子分组</h4>
            <div class="directory-grid">
              <button
                v-for="folder in directorySubfolders"
                :key="folder.id"
                type="button"
                class="directory-card"
                @click="openFolderDirectory(folder.id)"
              >
                <span class="directory-card-icon">📁</span>
                <span class="directory-card-title">{{ folder.name }}</span>
              </button>
            </div>
          </div>

          <div class="directory-section">
            <h4>文档</h4>
            <a-list v-if="directoryDocuments.length" :data-source="directoryDocuments" :split="false">
              <template #renderItem="{ item }">
                <a-list-item
                  class="directory-doc-item"
                  :class="{ 'directory-doc-item--nested': item.depth > 0 }"
                  :style="{ paddingLeft: `${12 + item.depth * 18}px` }"
                  @click="navigateToNote(item.id)"
                >
                  <a-list-item-meta :title="item.title || '未命名文档'" :description="item.summary || item.excerpt || '暂无摘要'" />
                </a-list-item>
              </template>
            </a-list>
            <EmptyState
              v-else
              title="此目录下暂无文档"
              description="在此分组中新建文档，或从其他目录移入"
              preset="note"
              compact
            >
              <a-dropdown>
                <a-button type="primary">+ 新建</a-button>
                <template #overlay>
                  <a-menu @click="handleCreateMenu">
                    <a-menu-item key="folder">新建子分组</a-menu-item>
                    <a-menu-item key="doc">新建文档</a-menu-item>
                    <a-menu-item key="import">导入剪藏</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </EmptyState>
          </div>
        </div>

        <div v-else class="editor-empty">
          <div class="editor-empty-inner">
            <h3>开始写</h3>
            <p class="editor-empty-hint">选左侧文档，或新建一篇</p>
            <a-space wrap>
              <a-button type="primary" @click="void createDocumentUnder(null, null)">新建文档</a-button>
              <a-button @click="importModalOpen = true">导入剪藏</a-button>
            </a-space>
          </div>
        </div>
      </a-spin>

      <div v-if="note" class="side-drawer-root" :class="{ 'is-open': aiPanelOpen }">
        <div class="side-drawer-backdrop" @click="closeAiPanel" />
        <aside class="side-drawer-panel">
          <NoteAiPanel
            ref="aiPanelRef"
            :note-id="note.id"
            :title="formState.title"
            :content="formState.content"
            :summary="note.summary"
            :workspace-id="note.workspaceId"
            :ai-enabled="aiEnabled"
            :get-selection="getEditorSelection"
            @apply-content="applyAiContent"
            @apply-summary="applyAiSummary"
            @replace-selection="replaceEditorSelection"
            @extract-todos="handleExtractTodos(note.id, $event)"
            @open-note="openNoteById"
            @close="closeAiPanel"
          />
        </aside>
      </div>

      <div v-if="note" class="side-drawer-root context-drawer" :class="{ 'is-open': contextDrawerOpen }">
        <div class="side-drawer-backdrop" @click="closeContextDrawer" />
        <aside class="side-drawer-panel context-drawer-panel">
          <NoteContextPanel
            :linked-todos="linkedTodos"
            :related-notes="relatedNotes"
            :ai-enabled="aiEnabled"
            :related-loading="relatedNotesLoading"
            @close="closeContextDrawer"
            @complete="completeLinkedTodo"
            @parallel="joinLinkedParallel"
            @extract-todos="handleExtractTodos(note.id, 'ai')"
            @create-todo="(h) => goCreateTodo(note.id, note.title, h)"
            @view-all-todos="goTodosForNote"
            @open-note="openNoteById"
            @refresh-related="refreshRelatedNotes"
          />
        </aside>
      </div>

      <div v-if="note" class="side-drawer-root attachment-drawer" :class="{ 'is-open': attachmentDrawerOpen }">
        <div class="side-drawer-backdrop" @click="closeAttachmentDrawer" />
        <aside class="side-drawer-panel attachment-drawer-panel">
          <header class="attachment-drawer-head">
            <h3>附件</h3>
            <a-button type="text" size="small" @click="closeAttachmentDrawer">关闭</a-button>
          </header>
          <NoteAttachmentPanel
            embedded
            :note-id="activeNoteId || undefined"
            :workspace-id="note.workspaceId || currentWorkspaceId || undefined"
            @updated="onAttachmentsUpdated"
            @insert="insertAttachmentMarkdown"
          />
        </aside>
      </div>
    </section>

    <a-modal
      v-model:open="folderModalOpen"
      :title="folderModalMode === 'create' ? '新建分组' : '重命名分组'"
      :ok-text="folderModalMode === 'create' ? '创建' : '保存'"
      cancel-text="取消"
      :confirm-loading="folderSaving"
      @ok="handleFolderModalOk"
    >
      <a-form layout="vertical">
        <a-form-item label="分组名称" required>
          <a-input v-model:value="folderForm.name" placeholder="请输入分组名称" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="propsModalOpen"
      title="文档属性"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="propsSaving"
      @ok="handleSaveProperties"
    >
      <a-form layout="vertical" :model="propsForm">
        <a-form-item label="所属分组">
          <a-select v-model:value="propsForm.folderId" allow-clear placeholder="顶级（无分组）" :options="folderOptions" />
        </a-form-item>
        <a-form-item label="标签">
          <a-select
            v-model:value="propsForm.tagIds"
            mode="multiple"
            allow-clear
            placeholder="选择标签"
            :options="tagOptions"
          />
        </a-form-item>
        <a-form-item label="摘要">
          <a-textarea v-model:value="propsForm.summary" :rows="4" placeholder="可手动编辑，或在右键菜单使用「AI 摘要」" />
        </a-form-item>
        <a-form-item label="状态">
          <a-space wrap>
            <a-tag :color="propsTargetNote?.isFavorite ? 'gold' : 'default'">
              {{ propsTargetNote?.isFavorite ? '已收藏' : '未收藏' }}
            </a-tag>
            <a-tag :color="propsTargetNote?.status === 1 ? 'red' : 'blue'">
              {{ propsTargetNote?.status === 1 ? '已归档' : '正常' }}
            </a-tag>
          </a-space>
        </a-form-item>
      </a-form>
    </a-modal>

    <NoteImportModal
      v-model:open="importModalOpen"
      :workspaces="workspaces"
      :default-workspace-id="currentWorkspaceId"
    />

    <ShareLinkModal
      v-model:open="shareModalOpen"
      resource-type="NOTE"
      :resource-id="note?.id"
      title="分享文档"
      description="创建链接后，持有链接的人可只读查看当前文档（不含附件侧栏与 AI 功能）。"
    />

    <TodoExtractReviewModal
      :open="extractReview.open"
      :source="extractReview.source"
      :loading="extractReview.loading"
      :confirming="extractReview.confirming"
      :note-title="extractReview.noteTitle"
      :suggestions="extractReview.suggestions"
      :on-confirm="handleConfirmExtract"
      @cancel="closeExtractReview"
    />

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
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  nextTick,
  onActivated,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from 'vue';
import { storeToRefs } from 'pinia';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import { message, Modal } from 'ant-design-vue';
import { DownOutlined, LinkOutlined, PaperClipOutlined, QuestionCircleOutlined, ShareAltOutlined, ThunderboltOutlined } from '@ant-design/icons-vue';
import dayjs from 'dayjs';
import NoteDocTree from '../../components/note/NoteDocTree.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import SelectionFloatingToolbar from '../../components/note/SelectionFloatingToolbar.vue';
import SidebarResizeHandle from '../../components/layout/SidebarResizeHandle.vue';

const MarkdownEditor = defineAsyncComponent(() => import('../../components/editor/MarkdownEditor.vue'));
const NoteAiPanel = defineAsyncComponent(() => import('../../components/note/NoteAiPanel.vue'));
const NoteAttachmentPanel = defineAsyncComponent(() => import('../../components/note/NoteAttachmentPanel.vue'));
const NoteContextPanel = defineAsyncComponent(() => import('../../components/note/NoteContextPanel.vue'));
const NoteImportModal = defineAsyncComponent(() => import('../../components/note/NoteImportModal.vue'));
const TodoExtractReviewModal = defineAsyncComponent(() => import('../../components/todo/TodoExtractReviewModal.vue'));
import ShareLinkModal from '../../components/share/ShareLinkModal.vue';
import { autoExtractSessionKey, isMeetingLikeNote } from '../../utils/meetingNote';
import { createFolder, deleteFolder, listFolders, patchFolderTree, updateFolder, type Folder } from '../../api/folders';
import {
  confirmExtractTodosByAi,
  previewExtractTodosByAi,
  summarizeNoteByAi,
  type ConfirmExtractTodoItem,
  type ExtractedTodoSuggestion,
  type ExtractTodosSource,
} from '../../api/ai';
import {
  confirmExtractTodosFromNote,
  createNote,
  deleteNote,
  getNote,
  listNotes,
  listRelatedNotes,
  patchNoteFavorite,
  patchNoteStatus,
  patchNoteTree,
  previewExtractTodosFromNote,
  updateNote,
  type Note,
} from '../../api/notes';
import {
  listTodos,
  patchTodoStatus,
  TODO_STATUS,
  type TodoItem,
} from '../../api/todos';
import { listTags, type Tag } from '../../api/tags';
import { useAiPrefsStore } from '../../store/aiPrefs';
import { useSidebarLayoutStore } from '../../store/sidebarLayout';
import { useTodoSummaryStore } from '../../store/todoSummary';
import { useWorkspaceStore } from '../../store/workspace';
import { useModuleTabsStore } from '../../store/moduleTabs';
import { buildNotesTabLabel, resolveNotesTabId } from '../../utils/moduleTabMeta';
import { buildNoteUpdatePayload } from '../../utils/noteForm';
import {
  NOTE_FILTER_EMPTY_COPY,
  NOTE_LIST_FILTER_OPTIONS,
  noteListQueryParams,
  parseNoteListFilter,
  type NoteListFilter,
} from '../../utils/noteListFilter';
import {
  parseTreeDropNode,
  cloneTreeSnapshot,
  resolveTreeMoveFromFinalTree,
  resolveTreeMoveFromTargetInfo,
  type HeTreeDropTargetInfo,
  type TreeNodeSnapshot,
} from '../../utils/noteTreeDrag';
import { isAutoHomepageNote } from '../../utils/onboarding';

type TreeNodeType = 'root' | 'folder' | 'note';
type CreateAnchor =
  | { kind: 'top' }
  | { kind: 'folder'; folderId: string }
  | { kind: 'note'; noteId: string; folderId: string | null };
type DirectoryDocumentRow = Note & { depth: number };

type TreeNodeData = {
  key: string;
  title: string;
  nodeType: TreeNodeType;
  rawId?: string;
  sortOrder?: number;
  children?: TreeNodeData[];
  isLeaf?: boolean;
};
type ContextMenuItem = { key: string; label?: string; danger?: boolean; divider?: boolean };

const router = useRouter();
const route = useRoute();
const sidebarLayout = useSidebarLayoutStore();
const todoSummary = useTodoSummaryStore();
const moduleTabs = useModuleTabsStore();

/** 仅当前模块标签对应的 NotesView 实例响应路由（keep-alive 下避免非激活实例污染状态） */
const isActiveTabInstance = () => {
  const wsId =
    currentWorkspaceId.value ||
    (typeof route.query.workspace === 'string' ? route.query.workspace : '');
  return moduleTabs.activeCacheKey === resolveNotesTabId(wsId);
};

const routeBelongsToThisInstance = () => {
  if (!isActiveTabInstance()) return false;
  const routeWs = typeof route.query.workspace === 'string' ? route.query.workspace : '';
  if (!currentWorkspaceId.value) return true;
  return !routeWs || routeWs === currentWorkspaceId.value;
};

const notes = ref<Note[]>([]);
const workspaceStore = useWorkspaceStore();
const aiPrefsStore = useAiPrefsStore();
const { items: workspaces } = storeToRefs(workspaceStore);
const { enabled: aiEnabled, autoExtractTodosOnSave } = storeToRefs(aiPrefsStore);
const folders = ref<Folder[]>([]);
const tags = ref<Tag[]>([]);
const note = ref<Note | null>(null);
const linkedTodos = ref<TodoItem[]>([]);
const relatedNotes = ref<Note[]>([]);
const relatedNotesLoading = ref(false);
const contextDrawerOpen = ref(false);
const propsTargetNote = ref<Note | null>(null);
const currentWorkspaceId = ref<string>();
const activeFilter = ref<NoteListFilter>('all');
const noteFilterOptions = NOTE_LIST_FILTER_OPTIONS;
const treeEmptyCopy = computed(() => NOTE_FILTER_EMPTY_COPY[activeFilter.value]);
const loading = ref(false);
const detailLoading = ref(false);
const aiPanelOpen = ref(false);
const attachmentDrawerOpen = ref(false);
const shareModalOpen = ref(false);
const attachmentCount = ref(0);
const aiPanelRef = ref<{ runSummarizeQuiet?: () => Promise<{ summary?: string } | void> } | null>(null);
const markdownEditorRef = ref<{
  getSelectedText?: () => string;
  scrollToRange?: (start: number, end: number) => void;
  insertSnippet?: (text: string) => void;
} | null>(null);
const saving = ref(false);
const folderSaving = ref(false);
const propsSaving = ref(false);
const searchKeyword = ref('');
const folderModalOpen = ref(false);
const importModalOpen = ref(false);
const folderModalMode = ref<'create' | 'rename'>('create');
const propsModalOpen = ref(false);
const folderForm = reactive({ name: '', parentId: null as string | null, folderId: '' });
const propsForm = reactive({
  folderId: undefined as string | undefined,
  tagIds: [] as string[],
  summary: '',
});
const expandedTreeKeys = ref<string[]>([]);
const displayTreeData = ref<TreeNodeData[]>([]);
const treeDropInFlight = ref(false);
const directoryFolderId = ref<string | null>(null);
const savedSnapshot = ref('');
const autoSavedVisible = ref(false);
const writeNudgeVisible = ref(false);
const nudgeSummarizing = ref(false);
const showRouteSearchAlert = ref(true);
const AUTO_SAVE_MS = 1500;
let autoSaveTimer: ReturnType<typeof setTimeout> | null = null;
let autoSavedHintTimer: ReturnType<typeof setTimeout> | null = null;
const contextMenu = reactive({
  open: false,
  x: 0,
  y: 0,
  node: null as TreeNodeData | null,
});

const formState = reactive({
  title: '',
  content: '',
});

const currentWorkspaceName = computed(() => {
  const workspace = workspaces.value.find((item) => item.id === currentWorkspaceId.value);
  return workspace?.name || '知识库';
});

const isWorkspaceHomepageNote = (item: Note) => {
  if (isAutoHomepageNote(item)) return true;
  const workspace = workspaces.value.find((w) => w.id === item.workspaceId);
  return !!workspace?.homeNoteId && workspace.homeNoteId === item.id;
};

const folderOptions = computed(() => folders.value.map((item) => ({ label: item.name, value: item.id })));
const tagOptions = computed(() => tags.value.map((item) => ({ label: item.name, value: item.id })));
const activeNoteId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''));
const treeCollapsed = computed(() => sidebarLayout.treeCollapsed);
const treePaneStyle = computed(() => ({
  width: `${sidebarLayout.treeWidth}px`,
}));

const selectedTreeKeys = computed(() => {
  if (activeNoteId.value) {
    return [`note-${activeNoteId.value}`];
  }
  if (directoryFolderId.value) return [`folder-${directoryFolderId.value}`];
  return [];
});

const createAnchor = computed((): CreateAnchor => {
  if (directoryFolderId.value) {
    return { kind: 'folder', folderId: directoryFolderId.value };
  }
  if (activeNoteId.value) {
    const target = findNoteById(activeNoteId.value);
    if (target && !isWorkspaceHomepageNote(target)) {
      return {
        kind: 'note',
        noteId: activeNoteId.value,
        folderId: target.folderId || null,
      };
    }
  }
  return { kind: 'top' };
});

const createAnchorLabel = computed(() => {
  const anchor = createAnchor.value;
  if (anchor.kind === 'folder') {
    const folder = folders.value.find((item) => item.id === anchor.folderId);
    return `将在「${folder?.name || '分组'}」下新建`;
  }
  if (anchor.kind === 'note') {
    const target = findNoteById(anchor.noteId);
    return `将在「${target?.title || '文档'}」下新建`;
  }
  return '将在顶级新建';
});

const contextBadgeCount = computed(() => linkedTodos.value.length + relatedNotes.value.length);

const buildNoteTreeNode = (item: Note, notesByParent: Map<string, Note[]>): TreeNodeData => {
  const childNotes = notesByParent.get(item.id) || [];
  const children = childNotes
    .slice()
    .sort((a, b) => (b.sortOrder ?? 0) - (a.sortOrder ?? 0))
    .map((child) => buildNoteTreeNode(child, notesByParent));
  return {
    key: `note-${item.id}`,
    title: item.title || '未命名文档',
    nodeType: 'note',
    rawId: item.id,
    sortOrder: item.sortOrder ?? 0,
    isLeaf: false,
    children: children.length ? children : undefined,
  };
};

const sortTreeChildren = (nodes: TreeNodeData[]): TreeNodeData[] =>
  [...nodes]
    .sort((a, b) => (b.sortOrder ?? 0) - (a.sortOrder ?? 0))
    .map((node) => ({
      ...node,
      children: node.children ? sortTreeChildren(node.children as TreeNodeData[]) : undefined,
    }));

const directoryContext = computed(() => {
  if (!directoryFolderId.value) return null;
  const folder = folders.value.find((item) => item.id === directoryFolderId.value);
  return {
    folderId: directoryFolderId.value,
    title: folder?.name || '分组',
  };
});

const directorySubfolders = computed(() => {
  if (!directoryFolderId.value) return [];
  return folders.value
    .filter((item) => item.parentId === directoryFolderId.value)
    .sort((a, b) => (b.sortOrder ?? 0) - (a.sortOrder ?? 0));
});

const resolveNoteFolderId = (item: Note): string | null => {
  if (item.folderId) return item.folderId;
  if (!item.parentId) return null;
  const parent = notes.value.find((note) => note.id === item.parentId);
  return parent ? resolveNoteFolderId(parent) : null;
};

const directoryDocuments = computed((): DirectoryDocumentRow[] => {
  if (!directoryFolderId.value) return [];
  const folderId = directoryFolderId.value;

  const inFolder = visibleNotes.value.filter(
    (item) => !isWorkspaceHomepageNote(item) && resolveNoteFolderId(item) === folderId,
  );
  const inFolderIds = new Set(inFolder.map((item) => item.id));

  const byParent = new Map<string | null, Note[]>();
  inFolder.forEach((item) => {
    const key = item.parentId ?? null;
    const siblings = byParent.get(key) || [];
    siblings.push(item);
    byParent.set(key, siblings);
  });

  const sortSiblings = (items: Note[]) =>
    [...items].sort((a, b) => (b.sortOrder ?? 0) - (a.sortOrder ?? 0));

  const rows: DirectoryDocumentRow[] = [];
  const visited = new Set<string>();

  const walk = (parentId: string | null, depth: number) => {
    sortSiblings(byParent.get(parentId) || []).forEach((item) => {
      if (visited.has(item.id)) return;
      visited.add(item.id);
      rows.push({ ...item, depth });
      walk(item.id, depth + 1);
    });
  };

  walk(null, 0);
  inFolder.forEach((item) => {
    if (visited.has(item.id)) return;
    if (item.parentId && inFolderIds.has(item.parentId)) return;
    rows.push({ ...item, depth: 0 });
  });

  return rows;
});

const directoryBreadcrumb = computed(() => {
  if (!directoryFolderId.value) return [];
  const trail: Folder[] = [];
  let current = folders.value.find((item) => item.id === directoryFolderId.value);
  while (current) {
    trail.unshift(current);
    current = current.parentId ? folders.value.find((item) => item.id === current!.parentId) : undefined;
  }
  return trail;
});

const activeContextMenuItems = computed(() => {
  if (!contextMenu.node) return [];
  return getContextMenuItems(contextMenu.node);
});

const visibleNotes = computed(() => {
  let list = notes.value.filter((item) => !isWorkspaceHomepageNote(item));
  if (activeFilter.value === 'recent') {
    list = list.slice(0, 20);
  }
  return list;
});

const treeData = computed<TreeNodeData[]>(() => {
  const folderNodes = new Map<string, TreeNodeData>();
  const rootFolders: TreeNodeData[] = [];
  const notesByParent = new Map<string, Note[]>();

  visibleNotes.value.forEach((item) => {
    if (!item.parentId) return;
    const siblings = notesByParent.get(item.parentId) || [];
    siblings.push(item);
    notesByParent.set(item.parentId, siblings);
  });

  folders.value.forEach((folder) => {
    folderNodes.set(folder.id, {
      key: `folder-${folder.id}`,
      title: folder.name,
      nodeType: 'folder',
      rawId: folder.id,
      sortOrder: folder.sortOrder ?? 0,
      isLeaf: false,
    });
  });

  folders.value.forEach((folder) => {
    const node = folderNodes.get(folder.id);
    if (!node) return;
    if (folder.parentId && folderNodes.has(folder.parentId)) {
      const parentNode = folderNodes.get(folder.parentId)!;
      if (!parentNode.children) parentNode.children = [];
      parentNode.children.push(node);
    } else {
      rootFolders.push(node);
    }
  });

  const rootNotes: TreeNodeData[] = [];
  visibleNotes.value.forEach((item) => {
    if (item.parentId) return;
    const node = buildNoteTreeNode(item, notesByParent);
    if (item.folderId && folderNodes.has(item.folderId)) {
      const folderNode = folderNodes.get(item.folderId)!;
      if (!folderNode.children) folderNode.children = [];
      folderNode.children.push(node);
    } else {
      rootNotes.push(node);
    }
  });

  folderNodes.forEach((node) => {
    if (node.children?.length) {
      node.children = sortTreeChildren(node.children as TreeNodeData[]);
    }
  });

  return sortTreeChildren([...rootFolders, ...rootNotes]);
});

watch(treeData, (val) => {
  if (treeDropInFlight.value) return;
  displayTreeData.value = cloneTreeSnapshot(val);
}, { immediate: true });

const isDirty = computed(() => {
  if (!note.value) return false;
  const snapshot = JSON.stringify({
    title: formState.title,
    content: formState.content,
  });
  return snapshot !== savedSnapshot.value;
});

const routeSearchKeyword = computed(() =>
  typeof route.query.q === 'string' ? route.query.q.trim() : '',
);

const parseRouteOffset = (value: unknown) => {
  if (typeof value !== 'string' || !value.trim()) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
};

const routeHighlightStart = computed(() => parseRouteOffset(route.query.start));
const routeHighlightEnd = computed(() => parseRouteOffset(route.query.end));

const contentHighlightRange = computed(() => {
  const start = routeHighlightStart.value;
  const end = routeHighlightEnd.value;
  const keyword = routeSearchKeyword.value;
  const content = formState.content;

  if (start == null) {
    return { start: undefined as number | undefined, end: undefined as number | undefined };
  }

  if (!keyword) {
    return { start, end };
  }

  const lowerContent = content.toLowerCase();
  const lowerKeyword = keyword.toLowerCase();
  const contentIdx = lowerContent.indexOf(lowerKeyword);
  if (contentIdx < 0) {
    return { start: undefined, end: undefined };
  }

  if (start <= content.length) {
    const matchEnd = end ?? start + keyword.length;
    const slice = content.slice(start, Math.min(matchEnd, content.length));
    if (slice.toLowerCase().includes(lowerKeyword)) {
      return { start, end: end ?? start + keyword.length };
    }
  }

  return { start: contentIdx, end: contentIdx + keyword.length };
});

const scrollEditorToHighlight = () => {
  const { start, end } = contentHighlightRange.value;
  if (start == null) return;
  markdownEditorRef.value?.scrollToRange?.(start, end);
};

const routeSearchMatchCount = computed(() => {
  const keyword = routeSearchKeyword.value;
  if (!keyword) return 0;
  const haystack = `${formState.title}\n${formState.content}`.toLowerCase();
  const needle = keyword.toLowerCase();
  let count = 0;
  let pos = 0;
  while ((pos = haystack.indexOf(needle, pos)) !== -1) {
    count += 1;
    pos += needle.length;
  }
  return count;
});

const searchContextText = computed(() => {
  if (routeHighlightStart.value != null) {
    return routeSearchKeyword.value
      ? `已定位到引用段落（关键词「${routeSearchKeyword.value}」）`
      : '已定位到 AI 引用段落';
  }
  if (routeSearchKeyword.value) {
    return routeSearchMatchCount.value > 0
      ? `来自搜索「${routeSearchKeyword.value}」，正文中共 ${routeSearchMatchCount.value} 处匹配`
      : `来自搜索「${routeSearchKeyword.value}」，正文中未找到匹配`;
  }
  return '';
});

const writeNudgeStorageKey = (noteId: string) => `noto-write-nudge-dismissed-${noteId}`;

const dismissWriteNudge = (persist = true) => {
  writeNudgeVisible.value = false;
  if (persist && note.value) {
    sessionStorage.setItem(writeNudgeStorageKey(note.value.id), '1');
  }
};

const maybeShowWriteNudge = () => {
  if (!note.value || !aiEnabled.value) {
    writeNudgeVisible.value = false;
    return;
  }
  if (sessionStorage.getItem(writeNudgeStorageKey(note.value.id))) {
    writeNudgeVisible.value = false;
    return;
  }
  const content = formState.content.trim();
  if (content.length < 150) {
    writeNudgeVisible.value = false;
    return;
  }
  const hasTodoMarkers = /[-*]\s*\[[ xX]\]/.test(content);
  const needsSummary = !note.value.summary?.trim();
  writeNudgeVisible.value = hasTodoMarkers || needsSummary;
};

const handleNudgeExtractTodos = async () => {
  if (!note.value) return;
  dismissWriteNudge(false);
  await handleExtractTodos(note.value.id, 'ai');
};

const handleNudgeSummarize = async () => {
  if (!note.value || !aiEnabled.value) return;
  nudgeSummarizing.value = true;
  try {
    const result = await summarizeNoteByAi(
      note.value.id,
      { title: formState.title, content: formState.content },
      false,
    );
    const summary = result.summary?.trim();
    if (!summary) {
      message.warning('未能生成摘要');
      return;
    }
    await applyAiSummary(summary);
    dismissWriteNudge();
  } catch (error: any) {
    message.error(error?.message || '生成摘要失败');
  } finally {
    nudgeSummarizing.value = false;
  }
};

const onSelectionTodoCreated = async () => {
  if (note.value?.id) {
    await loadLinkedTodos(note.value.id);
    todoSummary.refresh();
  }
};

const captureSnapshot = () => {
  savedSnapshot.value = JSON.stringify({
    title: formState.title,
    content: formState.content,
  });
};

const clearAutoSaveTimer = () => {
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer);
    autoSaveTimer = null;
  }
};

const showAutoSavedHint = () => {
  if (autoSavedHintTimer) {
    clearTimeout(autoSavedHintTimer);
  }
  autoSavedVisible.value = true;
  autoSavedHintTimer = setTimeout(() => {
    autoSavedVisible.value = false;
    autoSavedHintTimer = null;
  }, 2000);
};

const scheduleAutoSave = () => {
  clearAutoSaveTimer();
  if (!note.value || !isDirty.value || saving.value || detailLoading.value) return;
  autoSaveTimer = setTimeout(() => {
    void saveDetail({ silent: true, auto: true });
  }, AUTO_SAVE_MS);
};

const flushAutoSave = async () => {
  clearAutoSaveTimer();
  if (!note.value || !isDirty.value || saving.value) return;
  await saveDetail({ silent: true, auto: true });
};

const findNoteById = (id: string) => notes.value.find((item) => item.id === id) || null;

const getContextMenuItems = (node: TreeNodeData): ContextMenuItem[] => {
  if (node.nodeType === 'root') {
    return [
      { key: 'new-folder', label: '新建分组' },
      { key: 'new-doc', label: '新建文档' },
      { key: 'import-clip', label: '导入剪藏' },
    ];
  }
  if (node.nodeType === 'folder') {
    return [
      { key: 'open-directory', label: '浏览目录' },
      { key: 'new-subfolder', label: '新建子分组' },
      { key: 'new-doc', label: '新建文档' },
      { key: 'import-clip', label: '导入剪藏' },
      { key: 'rename-folder', label: '重命名分组' },
      { key: 'divider-1', divider: true },
      { key: 'delete-folder', label: '删除分组', danger: true },
    ];
  }
  const target = node.rawId ? findNoteById(node.rawId) : null;
  const isFavorite = target?.isFavorite ?? false;
  const isArchived = target?.status === 1;
  return [
    { key: 'new-subdoc', label: '新建子文档' },
    { key: 'note-props', label: '文档属性' },
    { key: 'favorite', label: isFavorite ? '取消收藏' : '收藏' },
    { key: 'archive', label: isArchived ? '取消归档' : '归档' },
    { key: 'ai-summary', label: 'AI 摘要' },
    { key: 'extract-todos', label: 'AI 提取待办' },
    { key: 'extract-todos-markdown', label: '规则提取待办' },
    { key: 'create-todo-action', label: '创建近期行动' },
    { key: 'create-todo-long', label: '创建长期待办' },
    { key: 'divider-2', divider: true },
    { key: 'delete-note', label: '删除文档', danger: true },
  ];
};

const openContextMenu = (event: MouseEvent, node: TreeNodeData) => {
  closeContextMenu();
  contextMenu.x = event.clientX;
  contextMenu.y = event.clientY;
  contextMenu.node = node;
  contextMenu.open = true;
};

const closeContextMenu = () => {
  contextMenu.open = false;
};

const onDocumentPointerDown = (event: MouseEvent) => {
  if (!contextMenu.open) return;
  const target = event.target as HTMLElement;
  if (target.closest('.tree-context-menu') || target.closest('.ant-dropdown-menu')) return;
  closeContextMenu();
};

const syncDocumentSidebar = (hasDocument: boolean) => {
  if (hasDocument) {
    sidebarLayout.onDocumentOpen();
  } else {
    sidebarLayout.onDocumentClose();
  }
};

const onTreeRightClick = (event: MouseEvent, node: TreeNodeData) => {
  event.preventDefault();
  event.stopPropagation();
  openContextMenu(event, node);
};

const onTreeBodyContextMenu = (event: MouseEvent) => {
  const target = event.target as HTMLElement;
  if (target.closest('.tree-node-row')) return;
  event.preventDefault();
  openContextMenu(event, {
    key: 'tree-root',
    title: currentWorkspaceName.value,
    nodeType: 'root',
  });
};

const onContextMenuSelect = async ({ key }: { key: string }) => {
  closeContextMenu();
  const node = contextMenu.node;
  if (!node) return;
  await handleTreeContextMenu(String(key), node);
};

const syncCurrentWorkspace = () => {
  const workspaceFromRoute = typeof route.query.workspace === 'string' ? route.query.workspace : '';
  if (workspaceFromRoute && workspaces.value.some((item) => item.id === workspaceFromRoute)) {
    currentWorkspaceId.value = workspaceFromRoute;
  } else if (!currentWorkspaceId.value && workspaces.value.length > 0) {
    currentWorkspaceId.value = workspaces.value[0].id;
  }
};

const loadFolders = async () => {
  if (!currentWorkspaceId.value) {
    folders.value = [];
    return;
  }
  folders.value = await listFolders(currentWorkspaceId.value);
};

const loadTags = async () => {
  if (!currentWorkspaceId.value) {
    tags.value = [];
    return;
  }
  tags.value = await listTags(currentWorkspaceId.value);
};

const loadNotes = async () => {
  if (!currentWorkspaceId.value) {
    notes.value = [];
    return;
  }
  loading.value = true;
  try {
    const page = await listNotes(
      noteListQueryParams(activeFilter.value, {
        page: 1,
        size: 500,
        workspaceId: currentWorkspaceId.value,
        keyword: searchKeyword.value || undefined,
      }),
    );
    notes.value = page.records;
    syncOpenNoteWithFilter();
  } catch (error: any) {
    message.error(error?.message || '加载文档失败');
  } finally {
    loading.value = false;
  }
};

const syncOpenNoteWithFilter = () => {
  if (!note.value) return;
  if (visibleNotes.value.some((item) => item.id === note.value!.id)) return;
  note.value = null;
  formState.title = '';
  formState.content = '';
  syncDocumentSidebar(false);
  const query = { ...route.query } as Record<string, string>;
  delete query.id;
  router.replace({ path: '/notes', query });
};

const onNoteFilterChange = () => {
  const query = { ...route.query } as Record<string, string>;
  if (activeFilter.value === 'all') {
    delete query.scope;
  } else {
    query.scope = activeFilter.value;
  }
  router.replace({ path: '/notes', query });
};

const reloadNotes = async () => {
  await loadNotes();
};

const loadLinkedTodos = async (noteId: string) => {
  try {
    const data = await listTodos({ page: 1, size: 20, noteId });
    linkedTodos.value = (data.records || []).filter(
      (item) => item.status !== TODO_STATUS.COMPLETED && item.status !== TODO_STATUS.CANCELLED,
    );
  } catch {
    linkedTodos.value = [];
  }
};

const loadRelatedNotes = async (noteId: string) => {
  relatedNotesLoading.value = true;
  try {
    relatedNotes.value = await listRelatedNotes(noteId, 8);
  } catch {
    relatedNotes.value = [];
  } finally {
    relatedNotesLoading.value = false;
  }
};

const refreshRelatedNotes = async () => {
  if (!note.value) return;
  await loadRelatedNotes(note.value.id);
};

const onAttachmentsUpdated = (count: number) => {
  attachmentCount.value = count;
};

const loadNoteDetail = async (id: string) => {
  if (!id) {
    note.value = null;
    linkedTodos.value = [];
    relatedNotes.value = [];
    attachmentCount.value = 0;
    syncDocumentSidebar(false);
    return;
  }
  if (note.value && note.value.id !== id) {
    await flushAutoSave();
  }
  autoSavedVisible.value = false;
  writeNudgeVisible.value = false;
  showRouteSearchAlert.value = true;
  detailLoading.value = true;
  try {
    const data = await getNote(id);
    if (route.params.id !== id) return;
    if (isWorkspaceHomepageNote(data)) {
      note.value = null;
      syncDocumentSidebar(false);
      goToNotesRoot();
      return;
    }
    note.value = data;
    const tabId = resolveNotesTabId(data.workspaceId || currentWorkspaceId.value);
    const wsName = workspaces.value.find((ws) => String(ws.id) === String(data.workspaceId))?.name;
    moduleTabs.updateTabLabel(tabId, buildNotesTabLabel(wsName, data.title));
    syncDocumentSidebar(true);
    formState.title = data.title;
    formState.content = data.content;
    captureSnapshot();
    await Promise.all([loadLinkedTodos(id), loadRelatedNotes(id)]);
    await maybeAutoExtractAfterImport();
  } catch (error: any) {
    message.error(error?.message || '加载文档失败');
    note.value = null;
    syncDocumentSidebar(false);
    router.replace({ path: '/notes', query: { ...route.query } });
  } finally {
    detailLoading.value = false;
    if (routeHighlightStart.value != null || routeSearchKeyword.value) {
      await nextTick();
      scrollEditorToHighlight();
    }
  }
};

const applyScope = () => {
  activeFilter.value = parseNoteListFilter(route.query.scope);
};

const clearDirectoryView = () => {
  directoryFolderId.value = null;
};

const openFolderDirectory = (folderId: string) => {
  directoryFolderId.value = folderId;
  note.value = null;
  formState.title = '';
  formState.content = '';
  syncDocumentSidebar(false);
  ensureExpanded(`folder-${folderId}`);
  const query: Record<string, string> = { folder: folderId };
  if (currentWorkspaceId.value) query.workspace = currentWorkspaceId.value;
  const scope = typeof route.query.scope === 'string' ? route.query.scope : '';
  if (scope) query.scope = scope;
  router.replace({ name: 'notes', query });
};

const goToNotesRoot = () => {
  clearDirectoryView();
  const query: Record<string, string> = {};
  if (currentWorkspaceId.value) query.workspace = currentWorkspaceId.value;
  const tabId = resolveNotesTabId(currentWorkspaceId.value);
  moduleTabs.updateTabLabel(tabId, buildNotesTabLabel(currentWorkspaceName.value));
  router.replace({ path: '/notes', query });
};

const syncDirectoryFromRoute = () => {
  const folderFromRoute = typeof route.query.folder === 'string' ? route.query.folder : '';
  if (folderFromRoute && folders.value.some((item) => item.id === folderFromRoute)) {
    directoryFolderId.value = folderFromRoute;
    return;
  }
  if (!activeNoteId.value && !folderFromRoute) {
    clearDirectoryView();
  }
};

const maybeAutoExtractAfterImport = async () => {
  if (route.query.extract !== '1' || !note.value || !aiEnabled.value) return;
  const nextQuery = { ...route.query };
  delete nextQuery.extract;
  router.replace({ path: route.path, query: nextQuery });
  if (sessionStorage.getItem(autoExtractSessionKey(note.value.id))) return;
  sessionStorage.setItem(autoExtractSessionKey(note.value.id), '1');
  await handleExtractTodos(note.value.id, 'ai');
};

const maybeAutoExtractAfterSave = async (options?: { auto?: boolean }) => {
  if (!note.value || !aiEnabled.value || !autoExtractTodosOnSave.value) return;
  if (options?.auto) return;
  if (sessionStorage.getItem(autoExtractSessionKey(note.value.id))) return;
  if (!isMeetingLikeNote(formState.title, formState.content)) return;
  sessionStorage.setItem(autoExtractSessionKey(note.value.id), '1');
  await handleExtractTodos(note.value.id, 'ai');
};

const consumeImportRouteQuery = () => {
  if (route.query.import !== '1') return;
  importModalOpen.value = true;
  const nextQuery = { ...route.query };
  delete nextQuery.import;
  router.replace({ path: route.path, query: nextQuery });
};

const consumeActionRouteQuery = async () => {
  if (route.query.action !== 'new-doc') return;
  const nextQuery = { ...route.query };
  delete nextQuery.action;
  router.replace({ path: route.path, query: nextQuery });
  await createDocumentUnder(null, null);
};

const bootstrap = async () => {
  try {
    applyScope();
    await Promise.all([
      workspaceStore.ensureLoaded(),
      aiPrefsStore.ensureLoaded(),
    ]);
    syncCurrentWorkspace();
    await Promise.all([loadFolders(), loadTags(), loadNotes()]);
    syncDirectoryFromRoute();
    if (activeNoteId.value) {
      await loadNoteDetail(activeNoteId.value);
    } else if (typeof route.query.folder === 'string') {
      openFolderDirectory(route.query.folder);
    }
    consumeImportRouteQuery();
    await consumeActionRouteQuery();
  } catch (error: any) {
    message.error(error?.message || '初始化失败');
  }
};

const syncWorkspaceFromRoute = async () => {
  if (!routeBelongsToThisInstance()) return;
  const workspaceFromRoute = typeof route.query.workspace === 'string' ? route.query.workspace : '';
  if (!workspaceFromRoute || workspaceFromRoute === currentWorkspaceId.value) return;
  if (!workspaces.value.some((item) => item.id === workspaceFromRoute)) return;
  currentWorkspaceId.value = workspaceFromRoute;
  activeFilter.value = 'all';
  note.value = null;
  formState.title = '';
  formState.content = '';
  clearDirectoryView();
  await Promise.all([loadFolders(), loadTags(), loadNotes()]);
  router.replace({ path: '/notes', query: { workspace: workspaceFromRoute } });
};

const syncActiveRouteState = async () => {
  if (!routeBelongsToThisInstance()) return;
  applyScope();
  const id = route.params.id;
  if (typeof id === 'string' && id) {
    clearDirectoryView();
    if (!note.value || note.value.id !== id) {
      await loadNoteDetail(id);
    }
    return;
  }
  if (route.query.dir || route.query.folder) {
    note.value = null;
    syncDocumentSidebar(false);
    syncDirectoryFromRoute();
    return;
  }
  note.value = null;
  syncDocumentSidebar(false);
};

const navigateToNote = (id: string) => {
  clearDirectoryView();
  const query: Record<string, string> = {};
  if (currentWorkspaceId.value) query.workspace = currentWorkspaceId.value;
  const scope = typeof route.query.scope === 'string' ? route.query.scope : '';
  if (scope) query.scope = scope;
  router.push({ path: `/notes/${id}`, query });
};

const goCreateTodo = (noteId: string, title: string, horizon: 'action' | 'long_term' = 'action') => {
  const query: Record<string, string> = {
    create: '1',
    noteId,
    title: title || '未命名文档',
    view: 'board',
    horizon,
  };
  if (currentWorkspaceId.value) query.workspace = currentWorkspaceId.value;
  router.push({ path: '/todos', query });
};

const goTodosForNote = () => {
  if (!note.value) return;
  const query: Record<string, string> = { noteId: note.value.id, view: 'board' };
  if (currentWorkspaceId.value) query.workspace = currentWorkspaceId.value;
  router.push({ path: '/todos', query });
};

const joinLinkedParallel = async (item?: TodoItem) => {
  if (!item) return;
  try {
    await patchTodoStatus(item.id, TODO_STATUS.IN_PROGRESS);
    message.success(`「${item.title}」已开始处理`);
    if (note.value?.id) await loadLinkedTodos(note.value.id);
    todoSummary.refresh();
  } catch (error: any) {
    message.error(error?.message || '操作失败');
  }
};

const completeLinkedTodo = async (item?: TodoItem) => {
  if (!item) return;
  try {
    await patchTodoStatus(item.id, TODO_STATUS.COMPLETED);
    message.success('做得好，又完成一项！');
    if (note.value?.id) {
      await loadLinkedTodos(note.value.id);
    }
    todoSummary.refresh();
  } catch (error: any) {
    message.error(error?.message || '更新失败');
  }
};

const extractReview = reactive({
  open: false,
  source: 'ai' as ExtractTodosSource,
  loading: false,
  confirming: false,
  noteId: '',
  noteTitle: '',
  suggestions: [] as ExtractedTodoSuggestion[],
});

const closeExtractReview = () => {
  extractReview.open = false;
  extractReview.loading = false;
  extractReview.confirming = false;
  extractReview.noteId = '';
  extractReview.noteTitle = '';
  extractReview.suggestions = [];
};

const handleExtractTodos = async (noteId: string, source: ExtractTodosSource = 'ai') => {
  extractReview.noteId = noteId;
  extractReview.source = source;
  extractReview.open = true;
  extractReview.loading = true;
  extractReview.suggestions = [];
  try {
    const preview =
      source === 'markdown'
        ? await previewExtractTodosFromNote(noteId)
        : await previewExtractTodosByAi(noteId);
    extractReview.noteTitle = preview.noteTitle || '';
    extractReview.suggestions = preview.suggestions || [];
    if (!extractReview.suggestions.length) {
      message.info(
        source === 'markdown'
          ? '文档中未发现待办（支持 - [ ] 或「待办：」）'
          : 'AI 未识别到待办项',
      );
      closeExtractReview();
    }
  } catch (error: any) {
    message.error(error?.message || '提取待办失败');
    closeExtractReview();
  } finally {
    extractReview.loading = false;
  }
};

const handleConfirmExtract = async (items: ConfirmExtractTodoItem[]) => {
  if (!extractReview.noteId) return;
  extractReview.confirming = true;
  const noteId = extractReview.noteId;
  try {
    const result =
      extractReview.source === 'markdown'
        ? await confirmExtractTodosFromNote(noteId, items)
        : await confirmExtractTodosByAi(noteId, items);
    if (result.createdCount === 0) {
      closeExtractReview();
      message.info(
        result.skippedCount > 0 ? '所选待办均已存在，未新增' : '未创建待办',
      );
      return;
    }
    await loadLinkedTodos(noteId);
    todoSummary.refresh();
    const skippedHint = result.skippedCount > 0 ? `，跳过 ${result.skippedCount} 条重复` : '';
    closeExtractReview();
    Modal.confirm({
      title: '已加入待办',
      content: `已创建 ${result.createdCount} 条待办${skippedHint}，是否前往行动看板？`,
      okText: '查看看板',
      cancelText: '继续编辑',
      onOk: () => router.push({ path: '/todos', query: { view: 'board', noteId } }),
    });
  } catch (error: any) {
    message.error(error?.message || '创建待办失败');
    throw error;
  } finally {
    extractReview.confirming = false;
  }
};

const ensureExpanded = (...keys: string[]) => {
  const merged = new Set([...expandedTreeKeys.value, ...keys]);
  expandedTreeKeys.value = [...merged];
};

const openCreateFolderModal = (parentId: string | null) => {
  folderModalMode.value = 'create';
  folderForm.name = '';
  folderForm.parentId = parentId;
  folderForm.folderId = '';
  folderModalOpen.value = true;
};

const handleCreateMenu = ({ key }: { key: string }) => {
  const anchor = createAnchor.value;
  if (key === 'folder') {
    if (anchor.kind === 'folder') {
      openCreateFolderModal(anchor.folderId);
    } else if (anchor.kind === 'note') {
      openCreateFolderModal(anchor.folderId);
    } else {
      openCreateFolderModal(null);
    }
    return;
  }
  if (key === 'import') {
    importModalOpen.value = true;
    return;
  }
  if (key === 'doc') {
    if (anchor.kind === 'note') {
      void createDocumentUnder(anchor.folderId, anchor.noteId);
    } else if (anchor.kind === 'folder') {
      void createDocumentUnder(anchor.folderId, null);
    } else {
      void createDocumentUnder(null, null);
    }
  }
};

const openRenameFolderModal = (folderId: string, name: string) => {
  folderModalMode.value = 'rename';
  folderForm.name = name;
  folderForm.folderId = folderId;
  folderModalOpen.value = true;
};

const handleFolderModalOk = async () => {
  if (!currentWorkspaceId.value || !folderForm.name.trim()) {
    message.warning('请输入分组名称');
    return Promise.reject();
  }
  folderSaving.value = true;
  try {
    if (folderModalMode.value === 'create') {
      await createFolder({
        workspaceId: currentWorkspaceId.value,
        name: folderForm.name.trim(),
        parentId: folderForm.parentId,
      });
      if (folderForm.parentId) {
        ensureExpanded(`folder-${folderForm.parentId}`);
      }
      message.success('分组已创建');
    } else {
      const folder = folders.value.find((item) => item.id === folderForm.folderId);
      if (!folder) return Promise.reject();
      await updateFolder(folderForm.folderId, {
        name: folderForm.name.trim(),
        parentId: folder.parentId ?? null,
      });
      message.success('分组已重命名');
    }
    await loadFolders();
    folderModalOpen.value = false;
  } catch (error: any) {
    message.error(error?.message || '操作失败');
    return Promise.reject();
  } finally {
    folderSaving.value = false;
  }
};

const createDocumentUnder = async (folderId: string | null, parentId: string | null = null) => {
  if (!currentWorkspaceId.value) return;
  try {
    const created = await createNote({
      title: parentId ? '未命名子文档' : '未命名文档',
      content: '',
      workspaceId: currentWorkspaceId.value,
      folderId: folderId || undefined,
      parentId: parentId || undefined,
    });
    await loadNotes();
    if (parentId) {
      ensureExpanded(`note-${parentId}`);
    } else if (folderId) {
      ensureExpanded(`folder-${folderId}`);
    }
    message.success(parentId ? '子文档已创建' : '文档已创建');
    navigateToNote(created.id);
  } catch (error: any) {
    message.error(error?.message || '创建文档失败');
  }
};

const resolveFolderIdFromNode = (node: TreeNodeData): string | null => {
  if (node.nodeType === 'folder') return node.rawId || null;
  if (node.nodeType === 'note') {
    const target = node.rawId ? findNoteById(node.rawId) : null;
    return target?.folderId || null;
  }
  return null;
};

const resolveCreateAnchorFromNode = (node: TreeNodeData): CreateAnchor => {
  if (node.nodeType === 'folder' && node.rawId) {
    return { kind: 'folder', folderId: node.rawId };
  }
  if (node.nodeType === 'note' && node.rawId) {
    const target = findNoteById(node.rawId);
    return { kind: 'note', noteId: node.rawId, folderId: target?.folderId || null };
  }
  return { kind: 'top' };
};

const handleTreeContextMenu = async (key: string, node: TreeNodeData) => {
  if (key === 'open-directory') {
    if (node.nodeType === 'folder' && node.rawId) {
      openFolderDirectory(node.rawId);
    }
    return;
  }
  if (key === 'new-folder') {
    openCreateFolderModal(node.nodeType === 'folder' ? node.rawId || null : null);
    return;
  }
  if (key === 'new-subfolder') {
    openCreateFolderModal(resolveFolderIdFromNode(node));
    return;
  }
  if (key === 'new-doc') {
    const anchor = resolveCreateAnchorFromNode(node);
    if (anchor.kind === 'note') {
      await createDocumentUnder(anchor.folderId, anchor.noteId);
    } else if (anchor.kind === 'folder') {
      await createDocumentUnder(anchor.folderId, null);
    } else {
      await createDocumentUnder(null, null);
    }
    return;
  }
  if (key === 'import-clip') {
    importModalOpen.value = true;
    return;
  }
  if (key === 'new-subdoc' && node.rawId) {
    const parentNote = findNoteById(node.rawId);
    await createDocumentUnder(parentNote?.folderId || null, node.rawId);
    return;
  }
  if (key === 'rename-folder' && node.rawId) {
    openRenameFolderModal(node.rawId, String(node.title));
    return;
  }
  if (key === 'delete-folder' && node.rawId) {
    Modal.confirm({
      title: '确认删除分组？',
      content: '分组内仍有文档时将无法删除。',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteFolder(node.rawId!);
          await loadFolders();
          message.success('分组已删除');
        } catch (error: any) {
          message.error(error?.message || '删除分组失败');
          return Promise.reject();
        }
      },
    });
    return;
  }
  if (!node.rawId) return;

  if (key === 'note-props') {
    await openNoteProperties(node.rawId);
    return;
  }
  if (key === 'favorite') {
    await mutateNote(node.rawId, 'favorite');
    return;
  }
  if (key === 'archive') {
    await mutateNote(node.rawId, 'archive');
    return;
  }
  if (key === 'ai-summary') {
    await mutateNote(node.rawId, 'ai-summary');
    return;
  }
  if (key === 'extract-todos') {
    await handleExtractTodos(node.rawId, 'ai');
    return;
  }
  if (key === 'extract-todos-markdown') {
    await handleExtractTodos(node.rawId, 'markdown');
    return;
  }
  if (key === 'create-todo-action') {
    goCreateTodo(node.rawId, String(node.title || ''), 'action');
    return;
  }
  if (key === 'create-todo-long') {
    goCreateTodo(node.rawId, String(node.title || ''), 'long_term');
    return;
  }
  if (key === 'delete-note') {
    Modal.confirm({
      title: '确认删除文档？',
      content: '删除后不可恢复。',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        await removeNote(node.rawId!);
      },
    });
  }
};

const openNoteProperties = async (noteId: string) => {
  propsSaving.value = false;
  try {
    const data = note.value?.id === noteId ? note.value : await getNote(noteId);
    propsTargetNote.value = data;
    propsForm.folderId = data.folderId || undefined;
    propsForm.tagIds = data.tags?.map((tag) => tag.id) || [];
    propsForm.summary = data.summary || '';
    propsModalOpen.value = true;
  } catch (error: any) {
    message.error(error?.message || '加载文档属性失败');
  }
};

const handleSaveProperties = async () => {
  if (!propsTargetNote.value) return Promise.reject();
  propsSaving.value = true;
  try {
    const target = propsTargetNote.value;
    const updated = await updateNote(
      target.id,
      buildNoteUpdatePayload(target, {
        title: target.title,
        content: target.content,
        summary: propsForm.summary,
        folderId: propsForm.folderId,
        tagIds: propsForm.tagIds,
      }),
    );
    propsTargetNote.value = updated;
    if (note.value?.id === updated.id) {
      note.value = updated;
    }
    await loadNotes();
    message.success('文档属性已保存');
  } catch (error: any) {
    message.error(error?.message || '保存属性失败');
    return Promise.reject();
  } finally {
    propsSaving.value = false;
  }
};

const mutateNote = async (noteId: string, action: 'favorite' | 'archive' | 'ai-summary') => {
  try {
    let updated: Note;
    const current = findNoteById(noteId);
    if (action === 'favorite') {
      updated = await patchNoteFavorite(noteId, !(current?.isFavorite ?? false));
      message.success(updated.isFavorite ? '已收藏' : '已取消收藏');
    } else if (action === 'archive') {
      const nextStatus = current?.status === 1 ? 0 : 1;
      updated = await patchNoteStatus(noteId, nextStatus);
      message.success(updated.status === 1 ? '已归档' : '已取消归档');
    } else {
      const result = await summarizeNoteByAi(
        noteId,
        note.value?.id === noteId
          ? { title: formState.title, content: formState.content }
          : undefined,
        true,
      );
      updated = note.value?.id === noteId
        ? { ...note.value, summary: result.summary }
        : await getNote(noteId);
      if (note.value?.id === noteId) {
        note.value = updated;
        propsForm.summary = result.summary;
      }
      message.success('AI 摘要已生成');
    }
    if (note.value?.id === noteId) {
      note.value = updated;
    }
    await loadNotes();
  } catch (error: any) {
    message.error(error?.message || '操作失败');
  }
};

const removeNote = async (noteId: string) => {
  try {
    await deleteNote(noteId);
    message.success('文档已删除');
    if (note.value?.id === noteId) {
      note.value = null;
      formState.title = '';
      formState.content = '';
      syncDocumentSidebar(false);
      router.replace({ path: '/notes', query: { ...route.query } });
    }
    await loadNotes();
  } catch (error: any) {
    message.error(error?.message || '删除失败');
    return Promise.reject();
  }
};

const handleTreeSelect = (key: string) => {
  if (key.startsWith('folder-')) {
    openFolderDirectory(key.replace('folder-', ''));
    return;
  }
  if (key.startsWith('note-')) {
    navigateToNote(key.replace('note-', ''));
  }
};

const revertDisplayTree = () => {
  displayTreeData.value = JSON.parse(JSON.stringify(treeData.value)) as TreeNodeData[];
};

const onTreeChange = async (payload: {
  dragKey: string;
  targetInfo: HeTreeDropTargetInfo | null;
  treeData: TreeNodeData[];
}) => {
  if (treeDropInFlight.value) return;
  if (activeFilter.value !== 'all') {
    message.info('请切换到「全部」后再拖拽排序');
    revertDisplayTree();
    return;
  }

  treeDropInFlight.value = true;
  displayTreeData.value = JSON.parse(JSON.stringify(payload.treeData)) as TreeNodeData[];

  const dragNode = parseTreeDropNode({ key: payload.dragKey });
  if (!dragNode?.rawId) {
    message.warning('无法识别拖拽节点');
    revertDisplayTree();
    treeDropInFlight.value = false;
    return;
  }

  const move = resolveTreeMoveFromTargetInfo(
    payload.targetInfo,
    payload.dragKey,
    folders.value,
    notes.value,
  ) ?? resolveTreeMoveFromFinalTree(
    displayTreeData.value as TreeNodeSnapshot[],
    payload.dragKey,
    folders.value,
    notes.value,
    null,
  );

  if (!move) {
    message.warning('无法移动到此位置');
    revertDisplayTree();
    treeDropInFlight.value = false;
    return;
  }

  try {
    if (move.type === 'folder') {
      await patchFolderTree(dragNode.rawId, move.payload);
    } else {
      await patchNoteTree(dragNode.rawId, move.payload);
    }
    await Promise.all([loadFolders(), loadNotes()]);
    if (move.type === 'folder' && move.payload.parentId) {
      ensureExpanded(`folder-${move.payload.parentId}`);
    } else if (move.type === 'note' && move.payload.folderId) {
      ensureExpanded(`folder-${move.payload.folderId}`);
    }
    if (move.type === 'note' && move.payload.parentId) {
      ensureExpanded(`note-${move.payload.parentId}`);
    }
    message.success('已更新位置');
  } catch (error: any) {
    message.error(error?.message || '移动失败');
    revertDisplayTree();
  } finally {
    treeDropInFlight.value = false;
  }
};

const syncFromNote = () => {
  if (!note.value) return;
  formState.title = note.value.title;
  formState.content = note.value.content || '';
  captureSnapshot();
  message.success('已恢复到已保存状态');
};

const applyAiContent = (content: string) => {
  formState.content = content;
};

const getEditorSelection = () => markdownEditorRef.value?.getSelectedText?.() ?? '';

const insertAttachmentMarkdown = (markdown: string) => {
  if (markdownEditorRef.value?.insertSnippet) {
    markdownEditorRef.value.insertSnippet(markdown);
    return;
  }
  formState.content = `${formState.content}${markdown}`;
};

const replaceEditorSelection = (payload: { original: string; replacement: string }) => {
  const idx = formState.content.indexOf(payload.original);
  if (idx < 0) {
    message.warning('原文已变更，请重新选中后再试');
    return;
  }
  formState.content =
    formState.content.slice(0, idx) +
    payload.replacement +
    formState.content.slice(idx + payload.original.length);
};

const applyAiSummary = async (summary: string) => {
  if (!note.value) return;
  note.value = { ...note.value, summary };
  propsForm.summary = summary;
  try {
    const updated = await updateNote(
      note.value.id,
      buildNoteUpdatePayload(note.value, {
        title: formState.title,
        content: formState.content,
        summary,
        folderId: note.value.folderId,
        tagIds: note.value.tags?.map((tag) => tag.id) || [],
      }),
    );
    note.value = updated;
    captureSnapshot();
    message.success('摘要已保存');
  } catch (error: any) {
    message.error(error?.message || '保存摘要失败');
  }
};

const openNoteById = (id: string) => {
  router.push({
    path: `/notes/${id}`,
    query: {
      workspace: currentWorkspaceId.value || undefined,
    },
  });
};

const maybeAutoSummarize = async () => {
  if (!note.value || !aiPanelRef.value) return;
  const result = await aiPanelRef.value.runSummarizeQuiet();
  if (!result?.summary?.trim()) return;
  try {
    const updated = await updateNote(
      note.value.id,
      buildNoteUpdatePayload(note.value, {
        title: formState.title,
        content: formState.content,
        summary: result.summary,
        folderId: note.value.folderId,
        tagIds: note.value.tags?.map((tag) => tag.id) || [],
      }),
    );
    note.value = updated;
    propsForm.summary = result.summary;
    await loadNotes();
  } catch {
    // 自动摘要失败不打断保存流程
  }
};

const saveDetail = async (options?: { silent?: boolean; auto?: boolean }) => {
  if (!note.value || saving.value) return false;
  if (!isDirty.value) return true;
  saving.value = true;
  try {
    const updated = await updateNote(
      note.value.id,
      buildNoteUpdatePayload(note.value, {
        title: formState.title,
        content: formState.content,
        summary: note.value.summary || '',
        folderId: note.value.folderId,
        tagIds: note.value.tags?.map((tag) => tag.id) || [],
      }),
    );
    note.value = updated;
    captureSnapshot();
    await loadNotes();
    await maybeAutoSummarize();
    if (options?.auto) {
      showAutoSavedHint();
    } else if (!options?.silent) {
      message.success('文档已保存');
    }
    await maybeAutoExtractAfterSave(options);
    maybeShowWriteNudge();
    return true;
  } catch (error: any) {
    message.error(error?.message || (options?.auto ? '自动保存失败' : '保存失败'));
    return false;
  } finally {
    saving.value = false;
  }
};

const closeAiPanel = () => {
  aiPanelOpen.value = false;
};

const openAiPanel = () => {
  attachmentDrawerOpen.value = false;
  contextDrawerOpen.value = false;
  aiPanelOpen.value = !aiPanelOpen.value;
};

const closeAttachmentDrawer = () => {
  attachmentDrawerOpen.value = false;
};

const closeContextDrawer = () => {
  contextDrawerOpen.value = false;
};

const toggleContextDrawer = () => {
  aiPanelOpen.value = false;
  attachmentDrawerOpen.value = false;
  contextDrawerOpen.value = !contextDrawerOpen.value;
};

const toggleAttachmentDrawer = () => {
  aiPanelOpen.value = false;
  contextDrawerOpen.value = false;
  attachmentDrawerOpen.value = !attachmentDrawerOpen.value;
};

const onEditorGlobalKeydown = (event: KeyboardEvent) => {
  const key = event.key.toLowerCase();
  if ((event.ctrlKey || event.metaKey) && key === 's') {
    if (note.value && isDirty.value && !saving.value) {
      event.preventDefault();
      void saveDetail();
    }
    return;
  }
  if (event.key !== 'Escape') return;
  if (aiPanelOpen.value) closeAiPanel();
  if (attachmentDrawerOpen.value) closeAttachmentDrawer();
  if (contextDrawerOpen.value) closeContextDrawer();
};

onMounted(() => {
  void bootstrap();
  document.addEventListener('mousedown', onDocumentPointerDown);
  document.addEventListener('scroll', closeContextMenu, true);
  window.addEventListener('resize', closeContextMenu);
  window.addEventListener('keydown', onEditorGlobalKeydown);
});

onActivated(() => {
  void syncActiveRouteState();
});

onBeforeRouteLeave(async () => {
  await flushAutoSave();
});

onUnmounted(() => {
  clearAutoSaveTimer();
  if (autoSavedHintTimer) {
    clearTimeout(autoSavedHintTimer);
  }
  document.removeEventListener('mousedown', onDocumentPointerDown);
  document.removeEventListener('scroll', closeContextMenu, true);
  window.removeEventListener('resize', closeContextMenu);
  window.removeEventListener('keydown', onEditorGlobalKeydown);
  sidebarLayout.onDocumentClose();
});

watch(
  () => route.query.q,
  () => {
    showRouteSearchAlert.value = true;
  },
);

watch(
  () =>
    [
      route.query.start,
      route.query.end,
      route.query.q,
      formState.content,
      detailLoading.value,
    ] as const,
  ([start, , , , loading]) => {
    if (loading || start == null) return;
    void nextTick(() => scrollEditorToHighlight());
  },
  { flush: 'post' },
);

watch(
  () => [formState.title, formState.content],
  () => scheduleAutoSave(),
);

watch(
  () => route.query.workspace,
  () => {
    if (!routeBelongsToThisInstance()) return;
    void syncWorkspaceFromRoute();
  },
);

watch(
  () => route.query.scope,
  async () => {
    if (!routeBelongsToThisInstance()) return;
    applyScope();
    await reloadNotes();
  },
);

watch(
  () => route.query.folder,
  (folder) => {
    if (!routeBelongsToThisInstance()) return;
    if (typeof folder === 'string' && folder) {
      directoryFolderId.value = folder;
    }
  },
);

watch(
  () => route.params.id,
  async (id) => {
    if (route.name !== 'notes') return;
    if (!routeBelongsToThisInstance()) return;
    if (typeof id === 'string' && id) {
      clearDirectoryView();
      await loadNoteDetail(id);
    } else if (route.query.dir || route.query.folder) {
      note.value = null;
      syncDocumentSidebar(false);
      syncDirectoryFromRoute();
    } else {
      note.value = null;
      syncDocumentSidebar(false);
    }
  },
);
</script>

<style scoped>
.notes-workspace {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  background: #fff;
  overflow: hidden;
}

.tree-pane {
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-right: none;
  background: #fbfcfe;
  overflow: hidden;
}

.tree-pane :deep(.ant-spin-nested-loading),
.tree-pane :deep(.ant-spin-container) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.tree-rail {
  width: 14px;
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
}

.tree-toolbar {
  padding: 14px 12px 10px;
  border-bottom: 1px solid #eef2f7;
  flex-shrink: 0;
}

.tree-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tree-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #101828;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1;
}

.tree-icon-btn {
  color: #98a2b3;
}

.tree-search {
  margin-top: 10px;
  margin-bottom: 8px;
}

.tree-filter {
  margin-bottom: 8px;
}

.tree-create-hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: #98a2b3;
  line-height: 1.4;
}

.tree-body {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px 6px 12px;
}

.tree-drag-hint {
  color: #667085;
}

.context-menu-anchor {
  position: fixed;
  width: 1px;
  height: 1px;
  pointer-events: none;
}

.editor-pane {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}

.editor-pane :deep(.editor-spin) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-pane :deep(.editor-spin > .ant-spin-container) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-shell {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.ai-drawer-root,
.side-drawer-root {
  position: absolute;
  inset: 0;
  z-index: 30;
  display: flex;
  justify-content: flex-end;
  pointer-events: none;
}

.ai-drawer-backdrop,
.side-drawer-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.1);
  backdrop-filter: blur(2px);
  opacity: 0;
  transition: opacity 0.22s ease;
  pointer-events: none;
}

.ai-drawer-panel,
.side-drawer-panel {
  position: relative;
  z-index: 1;
  width: min(288px, calc(100% - 16px));
  height: calc(100% - 16px);
  margin: 8px;
  border-radius: 14px;
  overflow: hidden;
  pointer-events: none;
  transform: translateX(calc(100% + 24px));
  transition: transform 0.24s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow:
    0 16px 48px rgba(15, 23, 42, 0.16),
    0 0 0 1px rgba(15, 23, 42, 0.06);
}

.ai-drawer-root.is-open,
.side-drawer-root.is-open {
  pointer-events: auto;
}

.ai-drawer-root.is-open .ai-drawer-backdrop,
.side-drawer-root.is-open .side-drawer-backdrop {
  opacity: 1;
  pointer-events: auto;
}

.ai-drawer-root.is-open .ai-drawer-panel,
.side-drawer-root.is-open .side-drawer-panel {
  transform: translateX(0);
  pointer-events: auto;
}

.ai-toggle--active {
  color: #6366f1 !important;
  background: rgba(99, 102, 241, 0.08) !important;
  border-radius: 6px;
}

.ai-toggle--active .action-label {
  font-weight: 600;
}

.editor-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  gap: 12px;
  min-height: 44px;
  padding: 6px 16px;
  border-bottom: 1px solid #f0f2f5;
  background: #fff;
}

.editor-status {
  display: flex;
  align-items: center;
  min-width: 72px;
  font-size: 12px;
}

.editor-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.action-label {
  margin-left: 4px;
}

.action-caret {
  margin-left: 2px;
  font-size: 10px;
  color: #98a2b3;
}

.topbar-divider {
  height: 16px;
  margin: 0 4px;
  border-color: #eaecf0;
}

.dirty-badge {
  color: #d48806;
  font-size: 12px;
}

.save-badge {
  color: #1677ff;
  font-size: 12px;
}

.saved-badge {
  color: #52c41a;
  font-size: 12px;
}

.note-todo-alert,
.search-context-alert {
  flex-shrink: 0;
  margin: 8px 20px 0;
  border-radius: 8px;
}

.search-context-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 20px;
  padding: 6px 10px;
  border-radius: 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
}

.write-nudge-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 20px;
  padding: 8px 12px;
  border-radius: 10px;
  background: linear-gradient(90deg, #f5f3ff 0%, #eff6ff 100%);
  border: 1px solid #e0e7ff;
}

.write-nudge-text {
  font-size: 12px;
  color: #4338ca;
}

.meta-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.meta-badge :deep(.ant-badge-count) {
  box-shadow: none;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  font-size: 11px;
}

.meta-dropdown {
  width: 280px;
  padding: 12px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
}

.meta-dropdown-title {
  margin: 0 0 8px;
  font-size: 12px;
  color: #94a3b8;
}

.linked-todo-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.linked-todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  background: #f8fafc;
}

.linked-todo-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #334155;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attachment-drawer-panel {
  display: flex;
  flex-direction: column;
  background: #fff;
  pointer-events: auto;
}

.context-drawer-panel {
  width: min(320px, calc(100% - 16px));
  display: flex;
  flex-direction: column;
  background: #fff;
  pointer-events: auto;
}

.attachment-drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #eef2f7;
}

.attachment-drawer-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.attachment-drawer-panel :deep(.attachment-panel) {
  flex: 1;
  overflow: auto;
}

.editor-title-row {
  flex-shrink: 0;
  padding: 12px 24px 10px;
  border-bottom: 1px solid #f0f2f5;
}

.title-field {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.35;
  color: #101828;
  padding: 0;
}

.title-field :deep(input) {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.35;
  color: #101828;
  padding: 0;
}

.title-field :deep(input::placeholder) {
  color: #c9cdd4;
  font-weight: 600;
}

.editor-canvas {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.editor-canvas :deep(.noto-markdown-editor) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-canvas :deep(.md-editor) {
  flex: 1;
  min-height: 0 !important;
  height: auto !important;
  border: none;
  border-radius: 0;
  box-shadow: none;
  display: flex;
  flex-direction: column;
}

.editor-canvas :deep(.md-editor-content) {
  flex: 1;
  min-height: 0;
}

.editor-canvas :deep(.md-editor-input-wrapper) {
  height: 100%;
}

.editor-canvas :deep(.md-editor .cm-scroller .cm-content) {
  min-height: auto !important;
}

.editor-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 0;
}

.editor-empty-inner {
  text-align: center;
  padding: 24px;
}

.editor-empty-inner h3 {
  margin: 0 0 6px;
  font-size: 18px;
  color: #101828;
}

.editor-empty-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: #94a3b8;
}

.directory-shell {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 24px 28px 32px;
}

.directory-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.directory-breadcrumb {
  flex: 1;
  min-width: 0;
}

.directory-section + .directory-section {
  margin-top: 28px;
}

.directory-section h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: #667085;
  font-weight: 600;
}

.directory-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.directory-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid #eaecf0;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.directory-card:hover {
  border-color: #91caff;
  box-shadow: 0 4px 12px rgba(22, 119, 255, 0.08);
}

.directory-card-icon {
  font-size: 18px;
}

.directory-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #101828;
}

.directory-doc-item {
  cursor: pointer;
  border-radius: 10px;
  padding-inline: 12px !important;
}

.directory-doc-item:hover {
  background: #f8fafc;
}

.directory-doc-item--nested :deep(.ant-list-item-meta-title) {
  font-weight: 500;
}
</style>
