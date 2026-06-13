import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router';
import router from '../router';
import { resolveModuleTabMeta, type ModuleTabIcon } from '../utils/moduleTabMeta';
import { useWorkspaceStore } from './workspace';

export interface ModuleTab {
  id: string;
  label: string;
  icon: ModuleTabIcon;
  path: string;
  query: Record<string, string | string[] | undefined>;
  cacheKey: string;
  closable: boolean;
  pinned: boolean;
}

const MAX_TABS = 10;
const PINNED_STORAGE_KEY = 'noto-module-tab-pins';

function loadPinnedIds(): Set<string> {
  try {
    const raw = localStorage.getItem(PINNED_STORAGE_KEY);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw) as string[];
    return new Set(Array.isArray(parsed) ? parsed : []);
  } catch {
    return new Set();
  }
}

function savePinnedIds(ids: Set<string>) {
  localStorage.setItem(PINNED_STORAGE_KEY, JSON.stringify([...ids]));
}

function queryValue(value: string | string[] | undefined) {
  if (Array.isArray(value)) return value.join(',');
  return value ?? '';
}

function isSameRouteQuery(
  left: Record<string, string | string[] | undefined>,
  right: Record<string, string | string[] | undefined>,
) {
  const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
  for (const key of keys) {
    if (queryValue(left[key]) !== queryValue(right[key])) {
      return false;
    }
  }
  return true;
}

export const useModuleTabsStore = defineStore('moduleTabs', () => {
  const tabs = ref<ModuleTab[]>([]);
  const activeId = ref<string | null>(null);
  const tabStack = ref<string[]>([]);
  const pinnedIds = ref<Set<string>>(loadPinnedIds());

  let suppressStack = false;
  let suppressSync = false;

  const activeTab = computed(() => tabs.value.find((tab) => tab.id === activeId.value) || null);
  const activeCacheKey = computed(() => activeTab.value?.cacheKey || 'default');
  const displayTabs = computed(() => sortTabs(tabs.value));
  const canGoBack = computed(() => tabStack.value.length > 0);

  function sortTabs(items: ModuleTab[]) {
    return [...items.filter((tab) => tab.pinned), ...items.filter((tab) => !tab.pinned)];
  }

  function applyPinnedState(tab: ModuleTab) {
    tab.pinned = pinnedIds.value.has(tab.id);
  }

  function reorderTabs() {
    tabs.value = sortTabs(tabs.value);
  }

  function workspaceNameForRoute(route: RouteLocationNormalized) {
    const workspaceId = typeof route.query.workspace === 'string' ? route.query.workspace : '';
    if (!workspaceId) return undefined;
    const workspaceStore = useWorkspaceStore();
    return workspaceStore.items.find((item) => String(item.id) === workspaceId)?.name;
  }

  function buildTab(route: RouteLocationNormalized): ModuleTab | null {
    const meta = resolveModuleTabMeta(route, workspaceNameForRoute(route));
    if (!meta) return null;
    const tab: ModuleTab = {
      id: meta.id,
      label: meta.label,
      icon: meta.icon,
      path: route.path,
      query: { ...route.query },
      cacheKey: meta.id,
      closable: meta.closable,
      pinned: false,
    };
    applyPinnedState(tab);
    return tab;
  }

  function syncRoute(route: RouteLocationNormalized) {
    if (suppressSync) return;
    const tab = buildTab(route);
    if (!tab) return;

    const nextId = tab.id;
    if (!suppressStack && activeId.value && activeId.value !== nextId) {
      const last = tabStack.value[tabStack.value.length - 1];
      if (last !== activeId.value) {
        tabStack.value.push(activeId.value);
      }
    }
    suppressStack = false;

    const existing = tabs.value.find((item) => item.id === tab.id);
    if (existing) {
      existing.label = tab.label;
      existing.path = tab.path;
      existing.query = tab.query;
      existing.icon = tab.icon;
      existing.closable = tab.closable;
    } else {
      tabs.value.push(tab);
      reorderTabs();
      if (tabs.value.length > MAX_TABS) {
        const removable = tabs.value.find(
          (item) => item.closable && !item.pinned && item.id !== nextId,
        );
        if (removable) {
          closeTab(removable.id, { silent: true });
        }
      }
    }
    activeId.value = nextId;
  }

  function isCurrentTabRoute(tab: ModuleTab) {
    const current = router.currentRoute.value;
    return current.path === tab.path && isSameRouteQuery(current.query, tab.query);
  }

  function activateTab(id: string, options?: { viaTabBar?: boolean }) {
    const tab = tabs.value.find((item) => item.id === id);
    if (!tab) return;
    if (options?.viaTabBar) {
      suppressStack = true;
    }

    activeId.value = id;

    if (isCurrentTabRoute(tab)) {
      return;
    }

    suppressSync = true;
    router
      .push({ path: tab.path, query: tab.query } as RouteLocationRaw)
      .catch(() => undefined)
      .finally(() => {
        suppressSync = false;
        if (isCurrentTabRoute(tab)) {
          syncRoute(router.currentRoute.value);
        }
      });
  }

  function openOrActivate(routeTarget: RouteLocationRaw, options?: { viaTabBar?: boolean }) {
    suppressSync = true;
    router.push(routeTarget).finally(() => {
      suppressSync = false;
    });
    if (options?.viaTabBar) {
      suppressStack = true;
    }
  }

  function findTab(id: string) {
    return tabs.value.find((tab) => tab.id === id);
  }

  function updateTabLabel(id: string, label: string) {
    const tab = tabs.value.find((item) => item.id === id);
    if (tab && label.trim()) {
      tab.label = label.trim();
    }
  }

  function goBack() {
    const prev = tabStack.value.pop();
    if (!prev || !findTab(prev)) return false;
    activateTab(prev, { viaTabBar: true });
    return true;
  }

  function togglePin(id: string) {
    const tab = tabs.value.find((item) => item.id === id);
    if (!tab) return;
    if (pinnedIds.value.has(id)) {
      pinnedIds.value.delete(id);
      tab.pinned = false;
    } else {
      pinnedIds.value.add(id);
      tab.pinned = true;
    }
    savePinnedIds(pinnedIds.value);
    reorderTabs();
  }

  function closeTabsByIds(ids: string[], options?: { silent?: boolean }) {
    const uniqueIds = [...new Set(ids)];
    const closingActive = uniqueIds.includes(activeId.value ?? '');
    for (const id of uniqueIds) {
      const index = tabs.value.findIndex((tab) => tab.id === id);
      if (index < 0) continue;
      const tab = tabs.value[index];
      if (!tab.closable) continue;
      tabs.value.splice(index, 1);
      tabStack.value = tabStack.value.filter((item) => item !== id);
    }
    if (!closingActive) return;
    const fallback = tabs.value[tabs.value.length - 1] || tabs.value[0];
    if (fallback) {
      activateTab(fallback.id, { viaTabBar: true });
      return;
    }
    activeId.value = null;
    if (!options?.silent) {
      suppressSync = true;
      router.push('/').finally(() => {
        suppressSync = false;
      });
    }
  }

  function closeOtherTabs(keepId: string) {
    const ids = tabs.value
      .filter((tab) => tab.id !== keepId && tab.closable)
      .map((tab) => tab.id);
    closeTabsByIds(ids);
    if (findTab(keepId) && activeId.value !== keepId) {
      activateTab(keepId, { viaTabBar: true });
    }
  }

  function closeTabsToRight(anchorId: string) {
    const ordered = displayTabs.value;
    const index = ordered.findIndex((tab) => tab.id === anchorId);
    if (index < 0) return;
    const ids = ordered
      .slice(index + 1)
      .filter((tab) => tab.closable)
      .map((tab) => tab.id);
    closeTabsByIds(ids);
  }

  function closeTab(id: string, options?: { silent?: boolean; force?: boolean }) {
    const index = tabs.value.findIndex((tab) => tab.id === id);
    if (index < 0) return;
    const tab = tabs.value[index];
    if (!tab.closable) return;
    if (tab.pinned && !options?.force) return;
    if (tab.pinned && options?.force) {
      pinnedIds.value.delete(id);
      savePinnedIds(pinnedIds.value);
    }

    tabs.value.splice(index, 1);
    tabStack.value = tabStack.value.filter((item) => item !== id);

    if (activeId.value !== id) return;

    const fallback = tabs.value[index] || tabs.value[index - 1] || tabs.value[0];
    if (fallback) {
      activateTab(fallback.id, { viaTabBar: true });
      return;
    }

    activeId.value = null;
    if (!options?.silent) {
      suppressSync = true;
      router.push('/').finally(() => {
        suppressSync = false;
      });
    }
  }

  function reset() {
    tabs.value = [];
    activeId.value = null;
    tabStack.value = [];
  }

  return {
    tabs,
    displayTabs,
    activeId,
    activeTab,
    activeCacheKey,
    canGoBack,
    syncRoute,
    activateTab,
    openOrActivate,
    findTab,
    updateTabLabel,
    goBack,
    togglePin,
    closeOtherTabs,
    closeTabsToRight,
    closeTab,
    reset,
  };
});
