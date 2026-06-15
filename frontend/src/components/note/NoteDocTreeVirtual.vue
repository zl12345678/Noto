<template>
  <div ref="scrollerRef" class="note-doc-tree-virtual" @scroll="onScroll">
    <div class="virtual-spacer" :style="{ height: `${totalHeight}px` }">
      <div
        v-for="row in visibleRows"
        :key="row.node.key"
        class="tree-node-row"
        :class="{
          'is-selected': selectedKeys.includes(row.node.key),
          'is-folder': row.node.nodeType === 'folder',
          'is-note': row.node.nodeType === 'note',
        }"
        :style="{ transform: `translateY(${row.offsetY}px)` }"
        @click="emit('select', row.node.key)"
        @contextmenu.prevent="emit('contextmenu', $event, row.node)"
      >
        <button
          v-if="row.node.children?.length"
          type="button"
          class="tree-open-btn"
          :aria-label="expandedKeys.includes(row.node.key) ? '折叠' : '展开'"
          @click.stop="toggleExpand(row.node.key)"
        >
          {{ expandedKeys.includes(row.node.key) ? '▾' : '▸' }}
        </button>
        <span v-else class="tree-open-spacer" />
        <span
          class="tree-node-label"
          :class="{
            'is-folder': row.node.nodeType === 'folder',
            'is-note': row.node.nodeType === 'note',
          }"
          :style="{ paddingLeft: `${row.depth * 16}px` }"
        >
          {{ row.node.title }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  flattenNoteTree,
  VIRTUAL_TREE_ROW_HEIGHT,
  type FlatTreeRow,
} from '../../utils/noteTreeFlatten';
import type { NoteTreeNode } from './NoteDocTree.vue';

const props = defineProps<{
  modelValue: NoteTreeNode[];
  selectedKeys: string[];
  expandedKeys: string[];
}>();

const emit = defineEmits<{
  select: [key: string];
  'update:expandedKeys': [keys: string[]];
  contextmenu: [event: MouseEvent, node: NoteTreeNode];
}>();

const scrollerRef = ref<HTMLElement | null>(null);
const scrollTop = ref(0);
const viewportHeight = ref(480);

const flatRows = computed<FlatTreeRow[]>(() =>
  flattenNoteTree(props.modelValue, props.expandedKeys),
);

const totalHeight = computed(() => flatRows.value.length * VIRTUAL_TREE_ROW_HEIGHT);

type VisibleRow = FlatTreeRow & { offsetY: number };

const visibleRows = computed<VisibleRow[]>(() => {
  const rows = flatRows.value;
  if (!rows.length) return [];
  const overscan = 6;
  const start = Math.max(0, Math.floor(scrollTop.value / VIRTUAL_TREE_ROW_HEIGHT) - overscan);
  const visibleCount =
    Math.ceil(viewportHeight.value / VIRTUAL_TREE_ROW_HEIGHT) + overscan * 2;
  const end = Math.min(rows.length, start + visibleCount);
  return rows.slice(start, end).map((row, index) => ({
    ...row,
    offsetY: (start + index) * VIRTUAL_TREE_ROW_HEIGHT,
  }));
});

function onScroll() {
  if (!scrollerRef.value) return;
  scrollTop.value = scrollerRef.value.scrollTop;
}

function toggleExpand(key: string) {
  if (props.expandedKeys.includes(key)) {
    emit(
      'update:expandedKeys',
      props.expandedKeys.filter((item) => item !== key),
    );
    return;
  }
  emit('update:expandedKeys', [...props.expandedKeys, key]);
}

function measureViewport() {
  viewportHeight.value = scrollerRef.value?.clientHeight || 480;
}

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  measureViewport();
  if (scrollerRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(measureViewport);
    resizeObserver.observe(scrollerRef.value);
  }
});

onUnmounted(() => {
  resizeObserver?.disconnect();
});

watch(
  () => props.modelValue.length,
  () => {
    scrollTop.value = scrollerRef.value?.scrollTop ?? 0;
  },
);

function scrollToKey(key: string) {
  const idx = flatRows.value.findIndex((row) => row.node.key === key);
  if (idx < 0 || !scrollerRef.value) return;
  const top = idx * VIRTUAL_TREE_ROW_HEIGHT;
  scrollerRef.value.scrollTop = Math.max(0, top - VIRTUAL_TREE_ROW_HEIGHT * 2);
}

defineExpose({ scrollToKey });
</script>

<style scoped>
.note-doc-tree-virtual {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 6px 12px;
}

.virtual-spacer {
  position: relative;
  width: 100%;
}

.tree-node-row {
  position: absolute;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 4px;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
}

.tree-node-row.is-selected {
  background: rgba(8, 145, 178, 0.12);
}

.tree-node-row:hover {
  background: var(--noto-hover-bg);
}

:root[data-theme='dark'] .tree-node-row:hover {
  background: rgba(255, 255, 255, 0.06);
}

:root[data-theme='dark'] .tree-node-row.is-selected {
  background: rgba(8, 145, 178, 0.22);
}

.tree-open-btn,
.tree-open-spacer {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
}

.tree-open-btn {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--noto-text-muted, #98a2b3);
  font-size: 11px;
  line-height: 16px;
  cursor: pointer;
}

.tree-node-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 22px;
}

.tree-node-label.is-folder {
  font-weight: 600;
  color: var(--noto-text, #344054);
}

.tree-node-label.is-note {
  color: var(--noto-text-muted, #475467);
}
</style>
