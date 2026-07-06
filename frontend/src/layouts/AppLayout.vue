<template>
  <a-layout class="app-layout">
    <div class="nav-shell">
      <a-layout-sider
        v-show="!navCollapsed"
        class="sider left-sider"
        :width="sidebarLayout.navWidth"
        :collapsed="false"
      >
      <div class="logo">
        <span class="logo-mark">N</span>
        <div class="logo-copy">
          <span class="logo-text">Noto</span>
        </div>
      </div>

      <div class="sider-nav">
        <div class="sider-section sider-section-compact">
          <p class="section-label">概览</p>
          <a-menu mode="inline" :selected-keys="selectedKeys" class="side-menu" @click="handleMenuClick">
            <a-menu-item key="dashboard">
              <span class="entry-item">
                <span class="entry-dot blue"></span>
                <span>首页</span>
              </span>
            </a-menu-item>
            <a-menu-item key="search">
              <span class="entry-item">
                <span class="entry-dot green"></span>
                <span>搜索</span>
              </span>
            </a-menu-item>
            <a-menu-item key="ai">
              <span class="entry-item">
                <span class="entry-dot cyan"></span>
                <span>AI 助手</span>
              </span>
            </a-menu-item>
            <a-menu-item key="ai-showcase">
              <span class="entry-item">
                <span class="entry-dot purple"></span>
                <span>AI 展示</span>
              </span>
            </a-menu-item>
          </a-menu>
        </div>

        <div class="sider-section sider-section-compact">
          <p class="section-label">任务</p>
          <a-menu mode="inline" :selected-keys="selectedKeys" class="side-menu" @click="handleMenuClick">
            <a-menu-item key="todos">
              <span class="entry-item entry-item-badge">
                <span class="entry-dot orange"></span>
                <span>待办中心</span>
                <a-badge
                  v-if="todoSummary.pendingCount > 0"
                  :count="todoSummary.pendingCount"
                  :overflow-count="99"
                  class="menu-badge"
                />
              </span>
            </a-menu-item>
            <a-menu-item key="reminders">
              <span class="entry-item">
                <span class="entry-dot purple"></span>
                <span>提醒</span>
              </span>
            </a-menu-item>
          </a-menu>
        </div>

        <div class="sider-section sider-section-compact">
          <p class="section-label">资料</p>
          <a-menu mode="inline" :selected-keys="selectedKeys" class="side-menu" @click="handleMenuClick">
            <a-menu-item key="drive">
              <span class="entry-item">
                <span class="entry-dot teal"></span>
                <span>网盘</span>
              </span>
            </a-menu-item>
            <a-menu-item key="my-shares">
              <span class="entry-item">
                <span class="entry-dot pink"></span>
                <span>我的分享</span>
              </span>
            </a-menu-item>
          </a-menu>
        </div>

        <div v-if="isAdmin" class="sider-section sider-section-compact">
          <p class="section-label">管理</p>
          <a-menu mode="inline" :selected-keys="selectedKeys" class="side-menu" @click="handleMenuClick">
            <a-menu-item key="admin-audit-logs">
              <span class="entry-item">
                <span class="entry-dot red"></span>
                <span>操作日志</span>
              </span>
            </a-menu-item>
          </a-menu>
        </div>

        <div class="sider-section knowledge-section">
          <div class="section-head">
            <p class="section-label">知识库</p>
            <a-button type="link" size="small" class="create-kb-btn" @click="openCreateModal">新建</a-button>
          </div>
          <a-input-search
            v-model:value="knowledgeSearch"
            allow-clear
            placeholder="筛选知识库"
            size="small"
            class="sider-search knowledge-filter"
          />
          <a-menu
            v-if="filteredKnowledgeBases.length > 0"
            mode="inline"
            :selected-keys="selectedKeys"
            class="side-menu knowledge-menu"
            @click="handleMenuClick"
          >
            <a-menu-item
              v-for="kb in filteredKnowledgeBases"
              :key="kb.key"
              @contextmenu.prevent="openKnowledgeContextMenu($event, kb)"
            >
              <span class="entry-item knowledge-entry">
                <span class="entry-dot" :class="kb.dotClass"></span>
                <span class="knowledge-name">{{ kb.label }}</span>
              </span>
            </a-menu-item>
          </a-menu>
          <div v-else class="knowledge-empty">
            {{ knowledgeSearch.trim() ? '未找到匹配的知识库' : '暂无知识库，点击新建' }}
          </div>
        </div>
      </div>
    </a-layout-sider>
      <div class="nav-rail">
        <SidebarResizeHandle
          inline
          :collapsed="navCollapsed"
          @toggle="sidebarLayout.toggleNav()"
          @resize="sidebarLayout.resizeNav"
        />
      </div>
    </div>
    <a-layout class="main-layout">
      <a-layout-header
        class="header"
        :class="{ 'header-compact': isNotesRoute || isAiRoute }"
      >
        <div class="header-left header-nav-controls">
          <a-tooltip :title="moduleBackTooltip">
            <a-button
              type="text"
              class="header-icon-btn"
              :disabled="!canModuleGoBack"
              @click="goBackModule"
            >
              <template #icon><ArrowLeftOutlined /></template>
            </a-button>
          </a-tooltip>
        </div>
        <div class="header-right">
          <a-input-search
            v-model:value="globalSearchKeyword"
            placeholder="搜索文档..."
            allow-clear
            class="header-search"
            @search="goGlobalSearch"
          />
          <a-badge
            :count="todoSummary.pendingCount"
            :overflow-count="99"
            :offset="[-4, 4]"
          >
            <a-button class="header-todo-btn" @click="router.push({ path: '/todos', query: { view: 'action' } })">
              <template #icon><CheckSquareOutlined /></template>
              待办
            </a-button>
          </a-badge>
          <a-dropdown placement="bottomRight">
            <a class="user-trigger" :class="{ 'user-trigger-compact': isNotesRoute || isAiRoute }" @click.prevent>
              <a-avatar
                :src="authStore.currentUser?.avatarUrl"
                :size="isNotesRoute || isAiRoute ? 32 : 40"
                :style="avatarStyle"
              >
                {{ avatarText }}
              </a-avatar>
              <div v-if="!isNotesRoute && !isAiRoute" class="user-meta">
                <span class="user-name">{{ displayName }}</span>
                <span class="user-email">{{ authStore.currentUser?.email || '未获取邮箱' }}</span>
              </div>
            </a>
            <template #overlay>
              <a-menu class="user-menu">
                <a-menu-item key="profile" @click="goProfile">个人中心</a-menu-item>
                <a-menu-item key="shortcuts" @click="shortcutsOpen = true">键盘快捷键</a-menu-item>
                <a-sub-menu key="theme" title="外观">
                  <a-menu-item
                    key="theme-light"
                    :class="{ 'theme-menu-active': themeStore.mode === 'light' }"
                    @click="themeStore.setMode('light')"
                  >
                    浅色
                  </a-menu-item>
                  <a-menu-item
                    key="theme-dark"
                    :class="{ 'theme-menu-active': themeStore.mode === 'dark' }"
                    @click="themeStore.setMode('dark')"
                  >
                    深色
                  </a-menu-item>
                  <a-menu-item
                    key="theme-system"
                    :class="{ 'theme-menu-active': themeStore.mode === 'system' }"
                    @click="themeStore.setMode('system')"
                  >
                    跟随系统
                  </a-menu-item>
                </a-sub-menu>
                <a-menu-item key="email" disabled>
                  {{ authStore.currentUser?.email || '未获取邮箱' }}
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" danger @click="handleLogout">退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>
      <ModuleTabBar />
      <a-layout-content
        class="content"
        :class="{ 'content-notes': isNotesRoute, 'content-ai': isAiRoute }"
      >
        <div v-if="showActionBanner" class="action-banner">
          <a-alert
            banner
            :type="todoSummary.overdueCount > 0 ? 'error' : 'warning'"
            show-icon
            :message="actionBannerMessage"
          >
            <template #action>
              <a-button size="small" type="primary" @click="goActionTodos">立即处理</a-button>
            </template>
          </a-alert>
        </div>
        <div
          class="noto-page-shell"
          :class="{ 'noto-page-shell--fluid': isNotesRoute || isAiRoute }"
        >
          <router-view v-slot="{ Component }">
            <keep-alive :max="10" :exclude="['AiView']">
              <component
                :is="Component"
                v-if="Component"
                :key="routeViewKey"
              />
            </keep-alive>
          </router-view>
        </div>
      </a-layout-content>
    </a-layout>
    <a-modal
      v-model:open="workspaceModalOpen"
      :title="workspaceModalMode === 'create' ? '新建知识库' : '编辑知识库'"
      :ok-text="workspaceModalMode === 'create' ? '创建' : '保存'"
      cancel-text="取消"
      :confirm-loading="workspaceSaving"
      @ok="handleSaveWorkspace"
    >
      <a-form layout="vertical" :model="workspaceForm">
        <a-form-item label="名称" required>
          <a-input v-model:value="workspaceForm.name" placeholder="请输入知识库名称" allow-clear />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="workspaceForm.description" placeholder="可选，简要说明用途" :rows="3" allow-clear />
        </a-form-item>
      </a-form>
    </a-modal>
    <CommandPalette
      v-model:open="commandPaletteOpen"
      :workspaces="workspaces"
      :default-workspace-id="primaryWorkspaceId"
    />
    <KeyboardShortcutsModal v-model:open="shortcutsOpen" />

    <a-dropdown
      v-model:open="knowledgeContextOpen"
      :trigger="[]"
      overlay-class-name="knowledge-context-dropdown"
    >
      <span
        class="knowledge-context-anchor"
        :style="{ left: `${knowledgeContextPos.x}px`, top: `${knowledgeContextPos.y}px` }"
      />
      <template #overlay>
        <a-menu @click="onKnowledgeContextMenuSelect">
          <a-menu-item key="edit">编辑</a-menu-item>
          <a-menu-item key="delete" danger>删除</a-menu-item>
        </a-menu>
      </template>
    </a-dropdown>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeftOutlined, CheckSquareOutlined } from '@ant-design/icons-vue';
import { message, Modal } from 'ant-design-vue';
import {
  createWorkspace,
  deleteWorkspace,
  updateWorkspace,
} from '../api/workspaces';
import { useAuthStore } from '../store/auth';
import { useAiPrefsStore } from '../store/aiPrefs';
import { useSidebarLayoutStore } from '../store/sidebarLayout';
import { useTodoSummaryStore } from '../store/todoSummary';
import { useWorkspaceStore } from '../store/workspace';
import { useReminderNotifier } from '../composables/useReminderNotifier';
import { useDigestNotifier } from '../composables/useDigestNotifier';
import { useOverdueNotifier } from '../composables/useOverdueNotifier';
import SidebarResizeHandle from '../components/layout/SidebarResizeHandle.vue';
import ModuleTabBar from '../components/layout/ModuleTabBar.vue';
import CommandPalette from '../components/command/CommandPalette.vue';
import KeyboardShortcutsModal from '../components/help/KeyboardShortcutsModal.vue';
import { useModuleTabsStore } from '../store/moduleTabs';
import { useThemeStore } from '../store/theme';
import { resolveModuleTabMeta } from '../utils/moduleTabMeta';

const authStore = useAuthStore();
const sidebarLayout = useSidebarLayoutStore();
const todoSummary = useTodoSummaryStore();
const workspaceStore = useWorkspaceStore();
const aiPrefsStore = useAiPrefsStore();
const moduleTabs = useModuleTabsStore();
const themeStore = useThemeStore();
useReminderNotifier();
useDigestNotifier();
useOverdueNotifier();
const router = useRouter();
const route = useRoute();
const knowledgeSearch = ref('');
const globalSearchKeyword = ref('');
const commandPaletteOpen = ref(false);
const shortcutsOpen = ref(false);
const workspaces = computed(() => workspaceStore.items);
const primaryWorkspaceId = computed(() => aiPrefsStore.primaryWorkspaceId);
const workspaceModalOpen = ref(false);
const workspaceModalMode = ref<'create' | 'edit'>('create');
const workspaceSaving = ref(false);
const editingWorkspaceId = ref<string | null>(null);
const workspaceForm = reactive({
  name: '',
  description: '',
});

type KnowledgeBaseItem = {
  key: string;
  label: string;
  workspaceId: string;
  dotClass: string;
  description?: string | null;
};

const knowledgeContextOpen = ref(false);
const knowledgeContextPos = reactive({ x: 0, y: 0 });
const knowledgeContextKb = ref<KnowledgeBaseItem | null>(null);

const DOT_CLASSES = ['blue', 'green', 'orange', 'purple', 'cyan'];

const knowledgeBases = computed<KnowledgeBaseItem[]>(() =>
  workspaces.value.map((ws, index) => ({
    key: `workspace:${ws.id}`,
    label: ws.name,
    workspaceId: String(ws.id),
    dotClass: DOT_CLASSES[index % DOT_CLASSES.length],
    description: ws.description,
  })),
);

const filteredKnowledgeBases = computed(() => {
  const keyword = knowledgeSearch.value.trim().toLowerCase();
  if (!keyword) return knowledgeBases.value;
  return knowledgeBases.value.filter((kb) => kb.label.toLowerCase().includes(keyword));
});

function workspaceNameForRoute() {
  const workspaceId = typeof route.query.workspace === 'string' ? route.query.workspace : '';
  if (!workspaceId) return undefined;
  return workspaces.value.find((item) => String(item.id) === workspaceId)?.name;
}

const currentTabMeta = computed(() => resolveModuleTabMeta(route, workspaceNameForRoute()));

/** 与当前路由一致的 cache key（勿用 activeTab.cacheKey，非标签页路由会错位） */
const routeViewKey = computed(() => {
  if (currentTabMeta.value) return currentTabMeta.value.id;
  return typeof route.name === 'string' ? route.name : route.fullPath;
});

const isNotesRoute = computed(() => route.name === 'notes');
const isAiRoute = computed(() => route.name === 'ai');
const isAdmin = computed(() => authStore.currentUser?.username === 'admin');
const isNoteDetailRoute = computed(
  () => route.name === 'notes' && typeof route.params.id === 'string' && route.params.id.length > 0,
);
const navCollapsed = computed(() => sidebarLayout.navCollapsed);

const canModuleGoBack = computed(
  () => moduleTabs.canGoBack || isNoteDetailRoute.value || route.name !== 'dashboard',
);

const moduleBackTooltip = computed(() => {
  if (moduleTabs.canGoBack) {
    return '返回上一模块（Alt+←，或点顶部标签）';
  }
  if (isNoteDetailRoute.value) {
    return '返回文档列表';
  }
  if (route.name !== 'dashboard') {
    return '返回首页';
  }
  return '已在首页';
});

const showActionBanner = computed(
  () =>
    todoSummary.pendingCount > 0 &&
    route.name !== 'todos' &&
    route.name !== 'dashboard' &&
    route.name !== 'ai',
);

const actionBannerMessage = computed(() => {
  if (todoSummary.overdueCount > 0) {
    return `你有 ${todoSummary.overdueCount} 项逾期、${todoSummary.pendingCount} 项待处理，现在完成一件吧`;
  }
  return `你有 ${todoSummary.pendingCount} 项待办，从最重要的一件开始`;
});

const goActionTodos = () => {
  router.push({ path: '/todos', query: { view: 'action' } });
};

watch(
  () => route.fullPath,
  () => {
    moduleTabs.syncRoute(route);
  },
  { immediate: true },
);

watch(
  () => route.name,
  (name) => {
    if (name !== 'notes') {
      sidebarLayout.onDocumentClose();
    }
    if (name === 'dashboard' || name === 'todos' || name === 'reminders') {
      todoSummary.refresh();
    }
  },
);

const selectedKeys = computed(() => {
  if (route.name === 'notes') {
    const workspace = typeof route.query.workspace === 'string' ? route.query.workspace : '';
    if (workspace) return [`workspace:${workspace}`];
  }
  if (route.name === 'dashboard') return ['dashboard'];
  if (route.name === 'search') return ['search'];
  if (route.name === 'drive') return ['drive'];
  if (route.name === 'my-shares') return ['my-shares'];
  if (route.name === 'todos') return ['todos'];
  if (route.name === 'reminders') return ['reminders'];
  if (route.name === 'ai') return ['ai'];
  if (route.name === 'ai-showcase') return ['ai-showcase'];
  if (route.name === 'admin-audit-logs') return ['admin-audit-logs'];
  if (route.name === 'profile') return [];
  return [];
});

const onGlobalKeydown = (event: KeyboardEvent) => {
  if (!event.key) return;
  const key = event.key.toLowerCase();
  if ((event.metaKey || event.ctrlKey) && key === 'k') {
    event.preventDefault();
    commandPaletteOpen.value = !commandPaletteOpen.value;
    return;
  }
  if (event.altKey && event.key === 'ArrowLeft') {
    event.preventDefault();
    goBackModule();
    return;
  }
  if (event.key === '?' && !isTypingTarget(event.target)) {
    event.preventDefault();
    shortcutsOpen.value = !shortcutsOpen.value;
  }
};

function isTypingTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
  return target.isContentEditable;
}

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeydown);
  try {
    await Promise.all([
      workspaceStore.ensureLoaded(),
      aiPrefsStore.ensureLoaded(),
      todoSummary.refresh(),
    ]);
  } catch {
    message.error('加载知识库列表失败');
  }
});

onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKeydown);
});

function openCreateModal() {
  workspaceModalMode.value = 'create';
  editingWorkspaceId.value = null;
  workspaceForm.name = '';
  workspaceForm.description = '';
  workspaceModalOpen.value = true;
}

function openEditModal(kb: KnowledgeBaseItem) {
  workspaceModalMode.value = 'edit';
  editingWorkspaceId.value = kb.workspaceId;
  workspaceForm.name = kb.label;
  workspaceForm.description = kb.description || '';
  workspaceModalOpen.value = true;
}

async function handleSaveWorkspace() {
  const name = workspaceForm.name.trim();
  if (!name) {
    message.warning('请输入知识库名称');
    return Promise.reject();
  }
  workspaceSaving.value = true;
  try {
    const payload = {
      name,
      description: workspaceForm.description.trim() || undefined,
    };
    if (workspaceModalMode.value === 'create') {
      const workspace = await createWorkspace(payload);
      await workspaceStore.refresh();
      knowledgeSearch.value = '';
      message.success('知识库已创建');
      router.push({ path: '/notes', query: { workspace: String(workspace.id) } });
      return;
    }
    if (!editingWorkspaceId.value) {
      return Promise.reject();
    }
    await updateWorkspace(editingWorkspaceId.value, payload);
    await workspaceStore.refresh();
    message.success('知识库已更新');
  } catch (error: any) {
    message.error(error?.message || (workspaceModalMode.value === 'create' ? '创建知识库失败' : '更新知识库失败'));
    return Promise.reject();
  } finally {
    workspaceSaving.value = false;
  }
}

function openKnowledgeContextMenu(event: MouseEvent, kb: KnowledgeBaseItem) {
  knowledgeContextKb.value = kb;
  knowledgeContextPos.x = event.clientX;
  knowledgeContextPos.y = event.clientY;
  knowledgeContextOpen.value = true;
}

function onKnowledgeContextMenuSelect({ key }: { key: string }) {
  knowledgeContextOpen.value = false;
  const kb = knowledgeContextKb.value;
  if (!kb) return;
  handleKnowledgeAction({ key }, kb);
}

function handleKnowledgeAction({ key }: { key: string }, kb: KnowledgeBaseItem) {
  if (key === 'edit') {
    openEditModal(kb);
    return;
  }
  if (key === 'delete') {
    confirmDeleteWorkspace(kb);
  }
}

function confirmDeleteWorkspace(kb: KnowledgeBaseItem) {
  Modal.confirm({
    title: '确认删除知识库？',
    content: `删除「${kb.label}」后，其中的文档、文件夹和标签将无法恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => handleDeleteWorkspace(kb),
  });
}

async function handleDeleteWorkspace(kb: KnowledgeBaseItem) {
  try {
    await deleteWorkspace(kb.workspaceId);
    await workspaceStore.refresh();
    message.success('知识库已删除');
    const currentWorkspace = typeof route.query.workspace === 'string' ? route.query.workspace : '';
    if (currentWorkspace === kb.workspaceId) {
      const nextWorkspace = workspaces.value[0];
      if (nextWorkspace) {
        router.push({ path: '/notes', query: { workspace: String(nextWorkspace.id) } });
      } else {
        router.push('/');
      }
    }
  } catch (error: any) {
    message.error(error?.message || '删除知识库失败');
    return Promise.reject();
  }
}

const displayName = computed(() => authStore.currentUser?.nickname || authStore.currentUser?.username || '用户');
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase());
const avatarStyle = computed(() => ({
  background: 'linear-gradient(135deg, #0891b2, #0ea5e9)',
}));

async function handleLogout() {
  await authStore.logout();
  message.success('已退出登录');
  router.push('/login');
}

function goProfile() {
  router.push('/profile');
}

function goGlobalSearch(value?: string) {
  const keyword = (value ?? globalSearchKeyword.value).trim();
  if (!keyword) {
    router.push('/search');
    return;
  }
  router.push({ path: '/search', query: { q: keyword } });
}

function goBackModule() {
  if (moduleTabs.goBack()) {
    return;
  }
  if (isNoteDetailRoute.value) {
    const workspace = typeof route.query.workspace === 'string' ? route.query.workspace : '';
    router.push(workspace ? { path: '/notes', query: { workspace } } : { path: '/notes' });
    return;
  }
  if (route.name !== 'dashboard') {
    router.push('/');
  }
}

function buildNotesQuery(scope?: string) {
  const query: Record<string, string> = {};
  const workspace = typeof route.query.workspace === 'string' ? route.query.workspace : '';
  if (workspace) query.workspace = workspace;
  if (scope) query.scope = scope;
  return query;
}

function activateModuleTab(tabId: string, fallback: () => void) {
  if (moduleTabs.findTab(tabId)) {
    moduleTabs.activateTab(tabId, { viaTabBar: true });
    return;
  }
  fallback();
}

function handleMenuClick({ key }: { key: string }) {
  if (key === 'dashboard') {
    activateModuleTab('dashboard', () => router.push('/'));
    return;
  }
  if (key === 'search') {
    activateModuleTab('search', () => router.push('/search'));
    return;
  }
  if (key === 'drive') {
    activateModuleTab('drive', () => {
      const workspace = typeof route.query.workspace === 'string' ? route.query.workspace : '';
      router.push(workspace ? { path: '/drive', query: { workspace } } : { path: '/drive' });
    });
    return;
  }
  if (key === 'my-shares') {
    activateModuleTab('my-shares', () => router.push('/shares'));
    return;
  }
  if (key === 'todos') {
    activateModuleTab('todos', () => router.push({ path: '/todos', query: { view: 'board' } }));
    return;
  }
  if (key === 'reminders') {
    activateModuleTab('reminders', () => router.push('/reminders'));
    return;
  }
  if (key === 'ai') {
    activateModuleTab('ai', () => router.push('/ai'));
    return;
  }
  if (key === 'ai-showcase') {
    activateModuleTab('ai-showcase', () => router.push('/ai-showcase'));
    return;
  }
  if (key === 'admin-audit-logs') {
    activateModuleTab('admin-audit-logs', () => router.push('/admin/audit-logs'));
    return;
  }
  if (key.startsWith('workspace:')) {
    const workspaceId = key.replace('workspace:', '');
    const tabId = `notes:${workspaceId}`;
    activateModuleTab(tabId, () => {
      router.push({ path: '/notes', query: { workspace: workspaceId } });
    });
    return;
  }
  if (key === 'notes:all') {
    router.push({ path: '/notes', query: buildNotesQuery('all') });
    return;
  }
  if (key === 'notes:folders') {
    router.push({ path: '/notes', query: buildNotesQuery('folders') });
    return;
  }
  if (key === 'notes:tags') {
    router.push({ path: '/notes', query: buildNotesQuery('tags') });
  }
}
</script>

<style scoped>
.app-layout {
  height: 100dvh;
  min-height: 100dvh;
  overflow: hidden;
  background: var(--noto-canvas, #f7f6f3);
}

.nav-shell {
  display: flex;
  flex-shrink: 0;
  height: 100%;
  overflow: visible;
  position: relative;
  z-index: 20;
}

.nav-rail {
  width: 14px;
  flex-shrink: 0;
  height: 100%;
  position: relative;
  z-index: 21;
}

.main-layout {
  position: relative;
  min-width: 0;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.left-sider {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  border-right: 1px solid var(--noto-border);
  padding: 12px 0;
  transition: all 0.35s cubic-bezier(0.32, 0.72, 0, 1);
  box-shadow: 2px 0 16px rgba(15, 23, 42, 0.04);
}

.left-sider :deep(.ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.sider-nav {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sider-section-compact {
  flex-shrink: 0;
}

.knowledge-filter {
  margin: 0 12px 8px;
}

.sider-search :deep(.ant-input),
.sider-search :deep(.ant-input-affix-wrapper),
.sider-search :deep(.ant-input-search-button) {
  border-radius: 10px;
}

.create-kb-btn {
  color: var(--noto-accent-deep) !important;
}

.knowledge-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding-bottom: 12px;
  overflow: hidden;
}

.knowledge-menu {
  flex: 1;
  overflow-y: auto;
}

.entry-item {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: 100%;
}

.entry-item-badge {
  width: 100%;
  justify-content: flex-start;
}

.menu-badge {
  margin-left: auto;
}

.entry-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.entry-dot.blue {
  background: #0891b2;
}

.entry-dot.green {
  background: #10b981;
}

.entry-dot.teal {
  background: #14b8a6;
}

.entry-dot.pink {
  background: #ec4899;
}

.entry-dot.orange {
  background: #f59e0b;
}

.entry-dot.purple {
  background: #8b5cf6;
}

.entry-dot.cyan {
  background: #06b6d4;
}

.entry-dot.red {
  background: #ef4444;
}

.entry-dot.indigo {
  background: #6366f1;
}

.knowledge-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-entry {
  width: 100%;
}

.knowledge-empty {
  margin: 0 12px;
  padding: 12px;
  border-radius: 12px;
  background: var(--noto-canvas);
  border: 1px solid var(--noto-border-soft);
  color: var(--noto-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 72px;
  padding: 0 20px 8px;
  font-weight: 700;
}

.logo-mark {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 800;
  background: linear-gradient(135deg, #0891b2, #0ea5e9);
  box-shadow: 0 4px 12px rgba(8, 145, 178, 0.3);
}

.logo-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.logo-text {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--noto-text);
}

.logo-subtext,
.section-label {
  font-size: 12px;
  color: var(--noto-text-muted);
  letter-spacing: 0.02em;
}

.sider-section {
  padding: 12px 12px 0;
}

.section-label {
  margin: 0;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.sider-section > .section-label {
  margin: 0 12px 8px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 12px 8px;
}

.create-kb-btn {
  height: auto;
  padding: 0;
  font-size: 12px;
}

.side-menu {
  border-right: none !important;
}

.side-menu.secondary {
  margin-top: -4px;
}

.sider-footer {
  padding: 16px 16px 8px;
  margin-top: auto;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  height: 48px !important;
  min-height: 48px !important;
  max-height: 48px !important;
  padding: 0 16px !important;
  line-height: 1;
  background: var(--noto-header-bg);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--noto-border);
  box-shadow: var(--noto-header-shadow);
}

.header-compact {
  height: 44px !important;
  min-height: 44px !important;
  max-height: 44px !important;
  padding: 0 12px !important;
}

.header-nav-controls {
  flex-direction: row;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  overflow: visible;
}

.header-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--noto-text-muted);
  flex-shrink: 0;
}

.header-icon-btn:hover {
  color: var(--noto-accent-deep);
  background: var(--noto-pastel-blue);
}

.header-left {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  min-width: 0;
  overflow: hidden;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.header-search {
  width: 240px;
}

.header-todo-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.header-compact .header-search {
  width: 200px;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px;
  border-radius: 12px;
  color: inherit;
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
}

.user-trigger-compact {
  padding: 2px 4px;
}

.user-trigger:hover {
  background: var(--noto-canvas);
  box-shadow: var(--noto-shadow-soft);
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-name {
  font-weight: 600;
  color: var(--noto-text);
}

.user-email {
  font-size: 12px;
  color: var(--noto-text-muted);
}

.content {
  flex: 1;
  min-height: 0;
  padding: 20px 16px;
  overflow: auto;
}

.action-banner {
  margin: -8px 0 16px;
}

.action-banner :deep(.ant-alert-banner) {
  border-radius: 12px;
}

.content-notes {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--noto-surface-solid);
  min-height: 0;
}

.content-notes .noto-page-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.content-ai {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--noto-surface-solid);
}

.content-ai .noto-page-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.user-menu {
  min-width: 220px;
}

.user-menu :deep(.theme-menu-active) {
  color: var(--noto-accent-deep);
  font-weight: 600;
}

.knowledge-context-anchor {
  position: fixed;
  width: 1px;
  height: 1px;
  pointer-events: none;
  z-index: 0;
}
</style>
