import { defineStore } from 'pinia';
import { computed, ref, watch } from 'vue';

const NAV_MIN = 200;
const NAV_MAX = 360;
const NAV_DEFAULT = 280;
const TREE_MIN = 180;
const TREE_MAX = 420;
const TREE_DEFAULT = 300;
const STORAGE_KEY = 'noto-sidebar-layout';

type StoredLayout = {
  navWidth?: number;
  treeWidth?: number;
};

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function readStoredLayout(): StoredLayout | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as StoredLayout;
  } catch {
    return null;
  }
}

function persistLayout(navWidth: number, treeWidth: number) {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ navWidth, treeWidth }),
  );
}

const stored = readStoredLayout();

export const useSidebarLayoutStore = defineStore('sidebarLayout', () => {
  const isDocumentOpen = ref(false);
  const navWidth = ref(
    clamp(stored?.navWidth ?? NAV_DEFAULT, NAV_MIN, NAV_MAX),
  );
  const treeWidth = ref(
    clamp(stored?.treeWidth ?? TREE_DEFAULT, TREE_MIN, TREE_MAX),
  );
  const navExpanded = ref(true);
  const treeExpanded = ref(true);

  let persistTimer: ReturnType<typeof setTimeout> | undefined;

  watch([navWidth, treeWidth], ([nav, tree]) => {
    if (persistTimer) clearTimeout(persistTimer);
    persistTimer = setTimeout(() => persistLayout(nav, tree), 200);
  });

  const navCollapsed = computed(() => !navExpanded.value);
  const treeCollapsed = computed(() => isDocumentOpen.value && !treeExpanded.value);

  function onDocumentOpen() {
    isDocumentOpen.value = true;
  }

  function onDocumentClose() {
    isDocumentOpen.value = false;
    treeExpanded.value = true;
  }

  function expandNav() {
    navExpanded.value = true;
  }

  function collapseNav() {
    navExpanded.value = false;
  }

  function toggleNav() {
    navExpanded.value = !navExpanded.value;
  }

  function expandTree() {
    treeExpanded.value = true;
  }

  function collapseTree() {
    if (isDocumentOpen.value) {
      treeExpanded.value = false;
    }
  }

  function toggleTree() {
    treeExpanded.value = !treeExpanded.value;
  }

  function resizeNav(delta: number) {
    if (navCollapsed.value) {
      navExpanded.value = true;
      navWidth.value = clamp(NAV_MIN + Math.max(delta, 0), NAV_MIN, NAV_MAX);
      return;
    }
    const next = navWidth.value + delta;
    if (next <= NAV_MIN - 12) {
      collapseNav();
      return;
    }
    navWidth.value = clamp(next, NAV_MIN, NAV_MAX);
  }

  function resizeTree(delta: number) {
    if (treeCollapsed.value) {
      treeExpanded.value = true;
      treeWidth.value = clamp(TREE_MIN + Math.max(delta, 0), TREE_MIN, TREE_MAX);
      return;
    }
    const next = treeWidth.value + delta;
    if (next <= TREE_MIN - 12) {
      collapseTree();
      return;
    }
    treeWidth.value = clamp(next, TREE_MIN, TREE_MAX);
  }

  return {
    navWidth,
    treeWidth,
    navCollapsed,
    treeCollapsed,
    isDocumentOpen,
    onDocumentOpen,
    onDocumentClose,
    expandNav,
    collapseNav,
    toggleNav,
    expandTree,
    collapseTree,
    toggleTree,
    resizeNav,
    resizeTree,
  };
});
