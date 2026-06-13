<template>
  <div v-if="displayTabs.length" class="module-tab-bar">
    <div class="module-tab-scroll">
      <button
        v-for="(tab, index) in displayTabs"
        :key="tab.id"
        type="button"
        class="module-tab"
        :class="{ active: tab.id === activeId, pinned: tab.pinned }"
        @click="moduleTabs.activateTab(tab.id, { viaTabBar: true })"
        @contextmenu.prevent="openContextMenu($event, tab, index)"
      >
        <span v-if="tab.pinned" class="module-tab-pin" title="已固定">📌</span>
        <span class="module-tab-icon" :class="`module-tab-icon--${tab.icon}`">{{ iconText(tab.icon) }}</span>
        <span class="module-tab-label">{{ tab.label }}</span>
        <span
          v-if="tab.closable && !tab.pinned"
          class="module-tab-close"
          role="button"
          aria-label="关闭标签"
          @click.stop="moduleTabs.closeTab(tab.id)"
        >
          ×
        </span>
      </button>
    </div>
    <div
      v-if="contextMenu.open"
      class="module-tab-context-menu"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      @mousedown.stop
    >
      <a-menu @click="handleMenu">
        <a-menu-item :key="contextMenu.tab?.pinned ? 'unpin' : 'pin'">
          {{ contextMenu.tab?.pinned ? '取消固定' : '固定标签' }}
        </a-menu-item>
        <a-menu-item key="close" :disabled="!contextMenu.tab?.closable">关闭</a-menu-item>
        <a-menu-divider />
        <a-menu-item key="closeOthers" :disabled="displayTabs.length <= 1">关闭其他</a-menu-item>
        <a-menu-item
          key="closeRight"
          :disabled="contextMenu.index < 0 || contextMenu.index >= displayTabs.length - 1"
        >
          关闭右侧
        </a-menu-item>
      </a-menu>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive } from 'vue';
import { storeToRefs } from 'pinia';
import { useModuleTabsStore, type ModuleTab } from '../../store/moduleTabs';
import type { ModuleTabIcon } from '../../utils/moduleTabMeta';

const moduleTabs = useModuleTabsStore();
const { displayTabs, activeId } = storeToRefs(moduleTabs);

const contextMenu = reactive({
  open: false,
  x: 0,
  y: 0,
  tab: null as ModuleTab | null,
  index: -1,
});

function iconText(icon: ModuleTabIcon) {
  switch (icon) {
    case 'home':
      return '⌂';
    case 'search':
      return '⌕';
    case 'ai':
      return '✦';
    case 'todo':
      return '☑';
    case 'reminder':
      return '⏰';
    case 'drive':
      return '▣';
    case 'share':
      return '⛓';
    case 'note':
      return '▤';
    default:
      return '•';
  }
}

function closeContextMenu() {
  contextMenu.open = false;
  contextMenu.tab = null;
  contextMenu.index = -1;
}

function openContextMenu(event: MouseEvent, tab: ModuleTab, index: number) {
  contextMenu.x = event.clientX;
  contextMenu.y = event.clientY;
  contextMenu.tab = tab;
  contextMenu.index = index;
  contextMenu.open = true;
}

function handleMenu(event: { key: string }) {
  const tab = contextMenu.tab;
  const index = contextMenu.index;
  closeContextMenu();
  if (!tab) return;

  switch (event.key) {
    case 'pin':
    case 'unpin':
      moduleTabs.togglePin(tab.id);
      break;
    case 'close':
      moduleTabs.closeTab(tab.id, { force: tab.pinned });
      break;
    case 'closeOthers':
      moduleTabs.closeOtherTabs(tab.id);
      break;
    case 'closeRight':
      moduleTabs.closeTabsToRight(tab.id);
      break;
    default:
      break;
  }
}

function onDocumentPointerDown(event: MouseEvent) {
  if (!contextMenu.open) return;
  const target = event.target as HTMLElement;
  if (target.closest('.module-tab-context-menu')) return;
  closeContextMenu();
}

onMounted(() => {
  document.addEventListener('mousedown', onDocumentPointerDown);
});

onUnmounted(() => {
  document.removeEventListener('mousedown', onDocumentPointerDown);
});
</script>

<style scoped>
.module-tab-bar {
  position: relative;
  flex-shrink: 0;
  border-bottom: 1px solid var(--noto-border, #e2e8f0);
  background: rgba(255, 255, 255, 0.96);
}

.module-tab-scroll {
  display: flex;
  align-items: stretch;
  gap: 4px;
  padding: 6px 12px;
  overflow-x: auto;
  scrollbar-width: thin;
}

.module-tab-scroll::-webkit-scrollbar {
  height: 4px;
}

.module-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 220px;
  padding: 5px 10px 5px 8px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition:
    background-color 0.15s,
    border-color 0.15s,
    color 0.15s;
  flex-shrink: 0;
}

.module-tab:hover {
  background: #e2e8f0;
  color: #0f172a;
}

.module-tab.active {
  background: rgba(8, 145, 178, 0.1);
  border-color: rgba(8, 145, 178, 0.28);
  color: #0e7490;
  font-weight: 600;
}

.module-tab.pinned {
  background: #fff;
  border-color: rgba(15, 23, 42, 0.08);
}

.module-tab.pinned.active {
  background: rgba(8, 145, 178, 0.12);
  border-color: rgba(8, 145, 178, 0.35);
}

.module-tab-pin {
  font-size: 10px;
  line-height: 1;
  flex-shrink: 0;
  opacity: 0.85;
}

.module-tab-icon {
  width: 18px;
  height: 18px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.06);
  flex-shrink: 0;
}

.module-tab.active .module-tab-icon {
  border-color: rgba(8, 145, 178, 0.2);
}

.module-tab-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-tab-close {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  color: #94a3b8;
  flex-shrink: 0;
}

.module-tab-close:hover {
  background: rgba(15, 23, 42, 0.08);
  color: #475569;
}

.module-tab-context-menu {
  position: fixed;
  z-index: 1200;
  min-width: 160px;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}
</style>
