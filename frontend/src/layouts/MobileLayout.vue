<template>
  <div class="mobile-layout">
    <header class="mobile-header">
      <div class="mobile-header-left">
        <a-button
          v-if="showBack"
          type="text"
          class="mobile-header-btn"
          aria-label="返回"
          @click="onBack"
        >
          <template #icon><ArrowLeftOutlined /></template>
        </a-button>
        <h1 class="mobile-header-title">{{ headerTitle }}</h1>
      </div>
      <div class="mobile-header-right">
        <a-dropdown
          v-model:open="moreMenuOpen"
          trigger="click"
          placement="bottomRight"
          :get-popup-container="getPopupContainer"
        >
          <a-button
            type="text"
            class="mobile-header-btn"
            aria-label="更多模块"
            aria-haspopup="menu"
            :aria-expanded="moreMenuOpen"
          >
            <template #icon><AppstoreOutlined /></template>
          </a-button>
          <template #overlay>
            <a-menu @click="onMoreMenuClick">
              <a-menu-item key="ai">AI 助手</a-menu-item>
              <a-menu-item key="drive">网盘</a-menu-item>
              <a-menu-item key="shares">我的分享</a-menu-item>
              <a-menu-item key="reminders">提醒</a-menu-item>
              <a-menu-item v-if="isAdmin" key="audit">操作日志</a-menu-item>
              <a-menu-divider />
              <a-menu-item key="dashboard">首页</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-button type="text" class="mobile-header-btn" aria-label="搜索" @click="goSearch">
          <template #icon><SearchOutlined /></template>
        </a-button>
      </div>
    </header>

    <main class="mobile-main" :class="{ 'mobile-main--ai': route.name === 'ai' }">
      <PwaInstallHint />
      <div class="mobile-router-outlet">
        <router-view v-slot="{ Component }">
          <keep-alive :max="8" :exclude="['AiView']">
            <component :is="Component" v-if="Component" :key="routeViewKey" />
          </keep-alive>
        </router-view>
      </div>
    </main>

    <nav class="mobile-tab-bar" aria-label="主导航">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="mobile-tab"
        :class="{ active: activeTab === tab.key }"
        @click="goTab(tab)"
      >
        <component :is="tab.icon" class="mobile-tab-icon" />
        <span class="mobile-tab-label">{{ tab.label }}</span>
        <a-badge
          v-if="tab.key === 'todos' && todoSummary.pendingCount > 0"
          :count="todoSummary.pendingCount"
          :overflow-count="99"
          class="mobile-tab-badge"
        />
      </button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ArrowLeftOutlined,
  HomeOutlined,
  ReadOutlined,
  CheckSquareOutlined,
  UserOutlined,
  SearchOutlined,
  AppstoreOutlined,
} from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';
import { useAiPrefsStore } from '../store/aiPrefs';
import { useTodoSummaryStore } from '../store/todoSummary';
import { useWorkspaceStore } from '../store/workspace';
import { useAuthStore } from '../store/auth';
import { useReminderNotifier } from '../composables/useReminderNotifier';
import { useDigestNotifier } from '../composables/useDigestNotifier';
import { useOverdueNotifier } from '../composables/useOverdueNotifier';
import { resolveModuleTabMeta } from '../utils/moduleTabMeta';
import { useModuleTabsStore } from '../store/moduleTabs';
import PwaInstallHint from '../components/mobile/PwaInstallHint.vue';

const moduleTabs = useModuleTabsStore();
const aiPrefsStore = useAiPrefsStore();
const todoSummary = useTodoSummaryStore();
const workspaceStore = useWorkspaceStore();
const authStore = useAuthStore();
useReminderNotifier();
useDigestNotifier();
useOverdueNotifier();

const router = useRouter();
const route = useRoute();
const moreMenuOpen = ref(false);

function getPopupContainer() {
  return document.body;
}

type TabKey = 'home' | 'notes' | 'todos' | 'me';

const tabs = [
  { key: 'home' as TabKey, label: '首页', icon: HomeOutlined, routeName: 'dashboard' },
  { key: 'notes' as TabKey, label: '笔记', icon: ReadOutlined, routeName: 'notes' },
  { key: 'todos' as TabKey, label: '待办', icon: CheckSquareOutlined, routeName: 'todos' },
  { key: 'me' as TabKey, label: '我的', icon: UserOutlined, routeName: 'profile' },
];

const activeTab = computed((): TabKey => {
  const name = route.name;
  if (name === 'dashboard') return 'home';
  if (name === 'notes') return 'notes';
  if (name === 'todos') return 'todos';
  if (name === 'profile' || name === 'reminders') return 'me';
  if (name === 'search') return 'home';
  if (name === 'ai' || name === 'drive' || name === 'my-shares' || name === 'admin-audit-logs') return 'me';
  return 'home';
});

const isAdmin = computed(() => authStore.currentUser?.username === 'admin');

const isNoteDetailRoute = computed(
  () => route.name === 'notes' && typeof route.params.id === 'string' && route.params.id.length > 0,
);

const showBack = computed(
  () =>
    isNoteDetailRoute.value ||
    route.name === 'search' ||
    route.name === 'reminders' ||
    route.name === 'ai' ||
    route.name === 'drive' ||
    route.name === 'my-shares' ||
    route.name === 'admin-audit-logs' ||
    (route.name === 'notes' && !isNoteDetailRoute.value && !!route.query.workspace),
);

function workspaceNameForRoute() {
  const workspaceId = typeof route.query.workspace === 'string' ? route.query.workspace : '';
  if (!workspaceId) return undefined;
  return workspaceStore.items.find((item) => String(item.id) === workspaceId)?.name;
}

const currentTabMeta = computed(() => resolveModuleTabMeta(route, workspaceNameForRoute()));

const routeViewKey = computed(() => {
  if (currentTabMeta.value) return currentTabMeta.value.id;
  return typeof route.name === 'string' ? route.name : route.fullPath;
});

const headerTitle = computed(() => {
  if (route.name === 'search') return '搜索';
  if (route.name === 'reminders') return '提醒';
  if (route.name === 'ai') return 'AI 助手';
  if (route.name === 'drive') return '网盘';
  if (route.name === 'my-shares') return '我的分享';
  if (route.name === 'admin-audit-logs') return '操作日志';
  if (route.name === 'profile') return '我的';
  if (route.name === 'todos') return '待办';
  if (route.name === 'notes') {
    if (isNoteDetailRoute.value) return '阅读';
    const ws = workspaceNameForRoute();
    return ws || '笔记';
  }
  if (route.name === 'dashboard') return '今日行动';
  return 'Noto';
});

function goTab(tab: (typeof tabs)[number]) {
  if (tab.key === 'notes') {
    const ws = aiPrefsStore.primaryWorkspaceId || workspaceStore.items[0]?.id;
    if (ws) {
      router.push({ path: '/notes', query: { workspace: String(ws) } });
    } else {
      router.push({ path: '/notes' });
    }
    return;
  }
  if (tab.key === 'todos') {
    router.push({ path: '/todos', query: { view: 'board' } });
    return;
  }
  if (tab.key === 'me') {
    router.push('/profile');
    return;
  }
  router.push('/');
}

function goSearch() {
  router.push('/search');
}

function onMoreMenuClick(info: { key: string | number }) {
  const key = String(info.key);
  moreMenuOpen.value = false;
  if (key === 'ai') {
    void router.push('/ai');
    return;
  }
  if (key === 'drive') {
    void router.push('/drive');
    return;
  }
  if (key === 'shares') {
    void router.push('/shares');
    return;
  }
  if (key === 'reminders') {
    void router.push('/reminders');
    return;
  }
  if (key === 'audit') {
    void router.push('/admin/audit-logs');
    return;
  }
  if (key === 'dashboard') {
    void router.push('/');
  }
}

function onBack() {
  if (
    route.name === 'search' ||
    route.name === 'reminders' ||
    route.name === 'ai' ||
    route.name === 'drive' ||
    route.name === 'my-shares' ||
    route.name === 'admin-audit-logs'
  ) {
    router.back();
    return;
  }
  if (isNoteDetailRoute.value) {
    const workspace = typeof route.query.workspace === 'string' ? route.query.workspace : '';
    router.push(workspace ? { path: '/notes', query: { workspace } } : { path: '/notes' });
    return;
  }
  if (route.name === 'notes') {
    router.push('/notes');
    return;
  }
  router.push('/');
}

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
    if (name === 'dashboard' || name === 'todos' || name === 'reminders') {
      todoSummary.refresh();
    }
  },
);

onMounted(async () => {
  try {
    await Promise.all([
      workspaceStore.ensureLoaded(),
      aiPrefsStore.ensureLoaded(),
      todoSummary.refresh(),
    ]);
  } catch {
    message.error('加载数据失败');
  }
});
</script>

<style scoped>
.mobile-layout {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  min-height: 100dvh;
  background: var(--noto-canvas, #f7f6f3);
  overflow: hidden;
}

.mobile-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 48px;
  padding: env(safe-area-inset-top, 0) 8px 0 4px;
  background: var(--noto-header-bg);
  border-bottom: 1px solid var(--noto-border);
}

.mobile-header-left {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.mobile-header-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--noto-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-header-right {
  flex-shrink: 0;
}

.mobile-header-btn {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.mobile-main {
  flex: 1;
  min-height: 0;
  overflow: auto;
  -webkit-overflow-scrolling: touch;
  padding: 12px 12px 8px;
}

/* AI 页自带 chat-log 滚动 + 底栏输入，外层勿再滚动 */
.mobile-main--ai {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  min-height: 0;
}

.mobile-main--ai :deep(.pwa-install-hint) {
  flex-shrink: 0;
  margin: 8px 12px 0;
}

.mobile-main--ai .mobile-router-outlet {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.mobile-main--ai .mobile-router-outlet > * {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.mobile-tab-bar {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  padding: 6px 8px calc(6px + env(safe-area-inset-bottom, 0));
  background: var(--noto-surface-solid);
  border-top: 1px solid var(--noto-border);
}

.mobile-tab {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 6px 4px;
  border: none;
  background: transparent;
  color: var(--noto-text-muted);
  font-size: 11px;
  touch-action: manipulation;
}

.mobile-tab.active {
  color: var(--noto-accent-deep);
  font-weight: 600;
}

.mobile-tab-icon {
  font-size: 20px;
}

.mobile-tab-label {
  line-height: 1.2;
}

.mobile-tab-badge {
  position: absolute;
  top: 2px;
  right: calc(50% - 22px);
}
</style>
